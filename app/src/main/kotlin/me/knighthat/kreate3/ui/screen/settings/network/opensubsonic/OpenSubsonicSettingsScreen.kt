/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.screen.settings.network.opensubsonic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.constant.SyncMode
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.MainSwitch
import me.knighthat.kreate3.ui.screen.settings.ProxySettings
import me.knighthat.kreate3.ui.screen.settings.RadioSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.StreamQualitySettings
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import me.knighthat.kreate3.ui.screen.settings.TextSetting
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** What the ping found, for the row under the address. */
@Composable
private fun CheckState.serverSummary(): String? = when (this) {
    CheckState.Idle -> null
    CheckState.Checking -> stringResource(R.string.setting_description_server_status_checking)
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> listOfNotNull(
            stringResource(R.string.setting_description_server_status_online),
            result.server.type?.replaceFirstChar { it.uppercase() },
            result.server.serverVersion,
        ).joinToString(" · ")

        is ServerCheck.Unreachable ->
            stringResource(R.string.setting_description_server_status_unreachable, result.reason)

        is ServerCheck.UntrustedCertificate -> stringResource(R.string.setting_description_server_status_certificate_untrusted)
        is ServerCheck.CertificateChanged -> stringResource(R.string.setting_description_server_status_certificate_changed)
        ServerCheck.NotSubsonic -> stringResource(
            R.string.setting_description_server_status_not_opensubsonic,
            stringResource(R.string.source_opensubsonicapi)
        )
        // Not checked, the row is hidden for these
        ServerCheck.Disabled, ServerCheck.NotConfigured, is ServerCheck.Rejected -> null
    }
}

/** What the credential check found, for the row under the credentials. */
@Composable
private fun CheckState.credentialsSummary(): String? = when (this) {
    CheckState.Idle -> null
    CheckState.Checking -> stringResource(R.string.setting_description_sign_in_status_checking)
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> stringResource(R.string.setting_description_sign_in_status_signed_in)
        is ServerCheck.Rejected -> when (result.code) {
            40 -> stringResource(R.string.setting_description_sign_in_status_wrong_credentials)
            44 -> stringResource(R.string.setting_description_sign_in_status_invalid_api_key)
            else -> result.message.ifEmpty {
                stringResource(R.string.setting_description_sign_in_status_refused, result.code)
            }
        }

        is ServerCheck.Unreachable ->
            stringResource(R.string.setting_description_sign_in_status_unreachable, result.reason)

        is ServerCheck.UntrustedCertificate -> stringResource(R.string.setting_description_sign_in_status_certificate_untrusted)
        is ServerCheck.CertificateChanged -> stringResource(R.string.setting_description_sign_in_status_certificate_changed)
        ServerCheck.NotSubsonic -> stringResource(
            R.string.setting_description_sign_in_status_not_opensubsonic,
            stringResource(R.string.source_opensubsonicapi)
        )
        ServerCheck.Disabled, ServerCheck.NotConfigured -> null
    }
}

/** The first pairs of a fingerprint, enough to tell two apart at a glance. */
/** The server converts songs above the bitrate, to the format it picked. */
private fun StreamQuality.openSubsonicLabel(): String = when (this) {
    StreamQuality.LOW -> "Low, up to 64 kbps"
    StreamQuality.MEDIUM -> "Medium, up to 128 kbps"
    StreamQuality.HIGH -> "High, up to 320 kbps, lossless songs are converted"
    StreamQuality.ORIGINAL -> "Original, the file as it is"
}

private fun String.shortFingerprint(): String = split(':').take(8).joinToString(":") + "…"

@Composable
private fun syncModeLabel(): (SyncMode) -> String {
    val off = stringResource(R.string.status_off)
    val all = stringResource(R.string.all)
    return { mode ->
        when (mode) {
            SyncMode.OFF -> off
            SyncMode.LIKED -> "Liked only"
            SyncMode.ALL -> all
        }
    }
}

/**
 * Asks whether to trust the certificate the server presents now in place of the trusted one.
 */
@Composable
private fun CertificateChangedDialog(
    change: ServerCheck.CertificateChanged,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_certificate_changed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.dialog_body_certificate_changed,
                        change.host,
                    )
                )
                Fingerprint(
                    stringResource(R.string.setting_title_trusted_until_now),
                    change.pinnedFingerprint
                )
                Fingerprint(
                    stringResource(R.string.setting_title_presented_now),
                    change.fingerprint
                )
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.action_trust_new_certificate)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
    )
}

/** A SHA-256 fingerprint under its [label], in a font that keeps the pairs aligned. */
@Composable
private fun Fingerprint(label: String, fingerprint: String) =
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = fingerprint,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }

/**
 * Asks whether to forget the trusted certificate.
 */
@Composable
private fun ForgetCertificateDialog(onForget: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_forget_certificate)) },
        text = { Text(stringResource(R.string.dialog_body_forget_certificate)) },
        confirmButton = { TextButton(onClick = onForget) { Text(stringResource(R.string.action_forget)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

/**
 * The outcome of a check in a row, with a spinner while it runs. A tap runs the checks again.
 */
@Composable
private fun CheckRow(title: String, state: CheckState, summary: String?, onRetry: () -> Unit) {
    summary ?: return
    SettingsItem(
        title = title,
        summary = summary,
        onClick = onRetry.takeIf { state is CheckState.Done },
        trailing = {
            if (state is CheckState.Checking)
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        },
    )
}

/**
 * Only an address starting with `http://` or `https://` and naming a host is accepted.
 * An empty one is, it clears the server.
 *
 * @return a check that gives the reason the address is not acceptable, `null` when it is
 */
@Composable
private fun serverUrlValidator(): (String) -> String? {
    val scheme = stringResource(R.string.setting_description_server_url_scheme)
    val host = stringResource(R.string.setting_description_server_url_host)
    return { url ->
        when {
            url.isEmpty() -> null
            !url.startsWith("https://") && !url.startsWith("http://") -> scheme
            url.substringAfter("://").isBlank() -> host
            else -> null
        }
    }
}

/**
 * The OpenSubsonic server: whether it is used, where it is, how to sign in, what of it is used and
 * the proxy to reach it through. Only the switch shows while it is off.
 */
@Composable
fun OpenSubsonicSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSubsonicSettingsViewModel = koinViewModel(),
) {
    val serverCheck by viewModel.server.collectFlowAsState()
    val allowSelfSigned by Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.collectAsStateWithLifecycle()
    val directPlay by Preferences.OPEN_SUBSONIC_DIRECT_PLAY.collectAsStateWithLifecycle()
    val certificateHost by Preferences.OPEN_SUBSONIC_CERTIFICATE_HOST.collectAsStateWithLifecycle()
    val certificate by Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256.collectAsStateWithLifecycle()
    val syncSongs by Preferences.OPEN_SUBSONIC_SYNC_SONGS.collectAsStateWithLifecycle()
    val syncAlbums by Preferences.OPEN_SUBSONIC_SYNC_ALBUMS.collectAsStateWithLifecycle()
    val syncArtists by Preferences.OPEN_SUBSONIC_SYNC_ARTISTS.collectAsStateWithLifecycle()
    val syncPlaylists by Preferences.OPEN_SUBSONIC_SYNC_PLAYLISTS.collectAsStateWithLifecycle()
    // Fingerprint of the change the user put off, it isn't asked again until they tap the row
    var putOff by rememberSaveable { mutableStateOf<String?>(null) }
    var askForget by remember { mutableStateOf(false) }
    val credentialsCheck by viewModel.credentials.collectFlowAsState()
    val enabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val auth by Preferences.OPEN_SUBSONIC_AUTH.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val username by Secrets.OPEN_SUBSONIC_USERNAME.collectAsStateWithLifecycle()
    val password by Secrets.OPEN_SUBSONIC_PASSWORD.collectAsStateWithLifecycle()
    val apiKey by Secrets.OPEN_SUBSONIC_API_KEY.collectAsStateWithLifecycle()

    SettingsPage(stringResource(R.string.source_opensubsonicapi), onBack, modifier) {
        MainSwitch(
            title = stringResource(
                R.string.setting_title_use_opensubsonic,
                stringResource(R.string.source_opensubsonicapi)
            ),
            summary = stringResource(R.string.setting_description_use_opensubsonic),
            checked = enabled,
            onCheckedChange = Preferences.OPEN_SUBSONIC_ENABLED::update,
        )

        // Saved values stay when hidden, they are used again once the setting is back on
        if (!enabled) return@SettingsPage

        SettingsSection(stringResource(R.string.setting_title_server))
        TextSetting(
            title = stringResource(R.string.setting_title_server_address),
            value = url,
            placeholder = "https://music.example.com",
            keyboardType = KeyboardType.Uri,
            validate = serverUrlValidator(),
            onSave = { Preferences.OPEN_SUBSONIC_URL.update(it.trim().trimEnd('/')) },
        )
        val change = (serverCheck as? CheckState.Done)?.result as? ServerCheck.CertificateChanged
        CheckRow(
            stringResource(R.string.setting_title_server),
            serverCheck,
            serverCheck.serverSummary()
        ) {
            if (change != null) putOff = null else viewModel.retry()
        }
        if (change != null && putOff != change.fingerprint)
            CertificateChangedDialog(
                change = change,
                onTrust = { viewModel.trust(change) },
                onDismiss = { putOff = change.fingerprint },
            )

        SwitchSetting(
            title = stringResource(R.string.setting_title_allow_self_signed_certificate),
            summary = stringResource(R.string.setting_description_allow_self_signed_certificate),
            checked = allowSelfSigned,
            onCheckedChange = Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED::update,
        )
        if (allowSelfSigned && certificate.isNotEmpty())
            SettingsItem(
                title = stringResource(R.string.setting_title_trusted_certificate),
                summary = "$certificateHost · ${certificate.shortFingerprint()}",
                onClick = { askForget = true },
            )
        if (askForget)
            ForgetCertificateDialog(
                onForget = {
                    askForget = false
                    viewModel.forgetCertificate()
                },
                onDismiss = { askForget = false },
            )

        SettingsSection(stringResource(R.string.setting_header_sign_in_with))
        RadioSetting(
            value = auth,
            options = OpenSubsonicAuth.entries,
            label = { if (it == OpenSubsonicAuth.PASSWORD) "Username and password" else "API key" },
            onSelect = Preferences.OPEN_SUBSONIC_AUTH::update,
        )
        when (auth) {
            OpenSubsonicAuth.PASSWORD -> {
                TextSetting(
                    title = stringResource(R.string.setting_title_username),
                    value = username,
                    onSave = { Secrets.OPEN_SUBSONIC_USERNAME.update(it.trim()) },
                )
                TextSetting(
                    title = stringResource(R.string.setting_title_password),
                    value = password,
                    summary = if (password.isEmpty()) "" else "•".repeat(8),
                    hidden = true,
                    onSave = Secrets.OPEN_SUBSONIC_PASSWORD::update,
                )
            }

            OpenSubsonicAuth.API_KEY -> TextSetting(
                title = stringResource(R.string.setting_title_api_key),
                value = apiKey,
                summary = if (apiKey.isEmpty()) "" else "•".repeat(8),
                hidden = true,
                onSave = Secrets.OPEN_SUBSONIC_API_KEY::update,
            )
        }
        CheckRow(
            stringResource(R.string.setting_title_sign_in),
            credentialsCheck,
            credentialsCheck.credentialsSummary(),
            viewModel::retry
        )

        SettingsSection(stringResource(R.string.setting_header_playback))
        SwitchSetting(
            title = stringResource(R.string.setting_title_direct_play),
            summary = stringResource(R.string.setting_description_direct_play),
            checked = directPlay,
            onCheckedChange = Preferences.OPEN_SUBSONIC_DIRECT_PLAY::update,
        )
        StreamQualitySettings(
            wifi = Preferences.OPEN_SUBSONIC_QUALITY_WIFI,
            mobile = Preferences.OPEN_SUBSONIC_QUALITY_MOBILE,
            label = StreamQuality::openSubsonicLabel,
        )

        SettingsSection(stringResource(R.string.setting_header_server_library))
        SwitchSetting(
            title = stringResource(R.string.setting_title_sync_liked_songs),
            summary = stringResource(R.string.setting_description_sync_songs_server),
            checked = syncSongs,
            onCheckedChange = viewModel::setSongSync,
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_show_playlists),
            summary = stringResource(R.string.setting_description_show_playlists_server),
            checked = syncPlaylists,
            onCheckedChange = Preferences.OPEN_SUBSONIC_SYNC_PLAYLISTS::update,
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_show_albums),
            value = syncAlbums,
            options = SyncMode.entries,
            label = syncModeLabel(),
            onSelect = Preferences.OPEN_SUBSONIC_SYNC_ALBUMS::update,
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_show_artists),
            value = syncArtists,
            options = SyncMode.entries,
            label = syncModeLabel(),
            onSelect = Preferences.OPEN_SUBSONIC_SYNC_ARTISTS::update,
        )

        ProxySettings(
            source = stringResource(R.string.setting_description_the_server),
            enabled = Preferences.OPEN_SUBSONIC_PROXY_ENABLED,
            type = Preferences.OPEN_SUBSONIC_PROXY_TYPE,
            host = Preferences.OPEN_SUBSONIC_PROXY_HOST,
            port = Preferences.OPEN_SUBSONIC_PROXY_PORT,
            username = Secrets.OPEN_SUBSONIC_PROXY_USERNAME,
            password = Secrets.OPEN_SUBSONIC_PROXY_PASSWORD,
        )
    }
}
