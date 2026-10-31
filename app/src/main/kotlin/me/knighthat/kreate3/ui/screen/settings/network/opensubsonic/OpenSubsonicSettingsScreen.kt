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
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerInfo
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.component.openSubsonicLabelRes
import me.knighthat.kreate3.ui.component.openSubsonicName
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.MainSwitch
import me.knighthat.kreate3.ui.screen.settings.NumberSetting
import me.knighthat.kreate3.ui.screen.settings.PreviewProxySettings
import me.knighthat.kreate3.ui.screen.settings.ProxySettings
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.StreamQualitySettings
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import me.knighthat.kreate3.ui.screen.settings.TextSetting
import me.knighthat.kreate3.ui.screen.settings.syncModeDescription
import me.knighthat.kreate3.ui.screen.settings.syncPlaylistsDescription
import me.knighthat.kreate3.ui.screen.settings.syncSongsDescription
import me.knighthat.kreate3.ui.screen.settings.syncTitle
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/**
 * What the ping found, for the row under the address.
 *
 * @param server what the server is called, see [openSubsonicName]
 */
@Composable
private fun CheckState.serverSummary(server: String): String? = when (this) {
    CheckState.Idle -> null
    CheckState.Checking -> stringResource(
        R.string.setting_description_server_status_checking,
        server
    )
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> listOfNotNull(
            stringResource(R.string.status_online),
            result.server.name,
            result.server.serverVersion,
        ).joinToString(" · ")

        is ServerCheck.Unreachable -> stringResource(
            R.string.setting_description_server_status_unreachable,
            server,
            result.reason
        )

        is ServerCheck.UntrustedCertificate ->
            stringResource(R.string.setting_description_server_status_certificate_untrusted, server)

        is ServerCheck.CertificateChanged ->
            stringResource(R.string.setting_description_server_status_certificate_changed, server)

        ServerCheck.NotSubsonic -> stringResource(
            R.string.setting_description_server_status_not_opensubsonic,
            stringResource(R.string.source_opensubsonicapi)
        )
        // Not checked, the row is hidden for these
        ServerCheck.Disabled, ServerCheck.NotConfigured, is ServerCheck.Rejected -> null
    }
}

/**
 * What the credential check found, for the row under the credentials.
 *
 * @param server what the server is called, see [openSubsonicName]
 */
@Composable
private fun CheckState.credentialsSummary(server: String): String? = when (this) {
    CheckState.Idle -> null
    CheckState.Checking -> stringResource(R.string.setting_description_sign_in_status_checking)
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> stringResource(R.string.status_signed_in)
        is ServerCheck.Rejected -> when (result.code) {
            40 -> stringResource(R.string.setting_description_sign_in_status_wrong_credentials)
            44 -> stringResource(R.string.setting_description_sign_in_status_invalid_api_key)
            else -> result.message.ifEmpty {
                stringResource(
                    R.string.setting_description_sign_in_status_refused,
                    server,
                    result.code
                )
            }
        }

        is ServerCheck.Unreachable -> stringResource(
            R.string.setting_description_server_status_unreachable,
            server,
            result.reason
        )

        is ServerCheck.UntrustedCertificate ->
            stringResource(
                R.string.setting_description_sign_in_status_certificate_untrusted,
                server
            )

        is ServerCheck.CertificateChanged ->
            stringResource(R.string.setting_description_sign_in_status_certificate_changed, server)

        ServerCheck.NotSubsonic -> stringResource(
            R.string.setting_description_server_status_not_opensubsonic,
            stringResource(R.string.source_opensubsonicapi)
        )
        ServerCheck.Disabled, ServerCheck.NotConfigured -> null
    }
}

/**
 * Where the sign in stands, for the row that opens [OpenSubsonicSignInScreen].
 *
 * @param server what the server is called, see [openSubsonicName]
 * @param serverSet whether the server's address is saved
 * @param credentialsSet whether every credential of [auth] is saved
 */
@Composable
private fun CheckState.signInSummary(
    server: String,
    auth: OpenSubsonicAuth,
    username: String,
    serverSet: Boolean,
    credentialsSet: Boolean,
): String = when {
    !credentialsSet && serverSet ->
        stringResource(R.string.setting_description_sign_in_status_start_syncing)

    !credentialsSet -> stringResource(R.string.setting_description_sign_in_status_not_signed_in)
    this is CheckState.Done && result is ServerCheck.Success -> when (auth) {
        OpenSubsonicAuth.PASSWORD ->
            stringResource(R.string.setting_description_sign_in_status_signed_in_as, username)

        OpenSubsonicAuth.API_KEY ->
            stringResource(R.string.setting_description_sign_in_status_signed_in_with_api_key)
    }
    // Not checked until the server answers, its row says why
    else -> credentialsSummary(server)
        ?: stringResource(R.string.setting_description_sign_in_status_waiting_for_server)
}

/** The first pairs of a fingerprint, enough to tell two apart at a glance. */
private fun String.shortFingerprint(): String = split(':').take(8).joinToString(":") + "…"

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
 *
 * @param server what the server is called, see [openSubsonicName]
 */
@Composable
private fun ForgetCertificateDialog(server: String, onForget: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_forget_certificate)) },
        text = { Text(stringResource(R.string.dialog_body_forget_certificate, server)) },
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
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSubsonicSettingsViewModel = koinViewModel(),
) {
    val serverCheck by viewModel.server.collectFlowAsState()
    val allowSelfSigned by Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.collectAsStateWithLifecycle()
    val directPlay by Preferences.OPEN_SUBSONIC_DIRECT_PLAY.collectAsStateWithLifecycle()
    val songCacheSize by GlobalPreferences.MEDIA3_CACHE_SIZE.collectAsStateWithLifecycle()
    val replayGainLufs by Preferences.OPEN_SUBSONIC_REPLAY_GAIN_LUFS.collectAsStateWithLifecycle()
    val certificateHost by Preferences.OPEN_SUBSONIC_CERTIFICATE_HOST.collectAsStateWithLifecycle()
    val certificate by Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256.collectAsStateWithLifecycle()
    val syncSongs by Preferences.OPEN_SUBSONIC_SYNC_SONGS.collectAsStateWithLifecycle()
    val syncAlbums by Preferences.OPEN_SUBSONIC_SYNC_ALBUMS.collectAsStateWithLifecycle()
    val syncArtists by Preferences.OPEN_SUBSONIC_SYNC_ARTISTS.collectAsStateWithLifecycle()
    val syncPlaylists by Preferences.OPEN_SUBSONIC_SYNC_PLAYLISTS.collectAsStateWithLifecycle()
    val credentialsCheck by viewModel.credentials.collectFlowAsState()
    val enabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val auth by Preferences.OPEN_SUBSONIC_AUTH.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val username by Secrets.OPEN_SUBSONIC_USERNAME.collectAsStateWithLifecycle()
    val password by Secrets.OPEN_SUBSONIC_PASSWORD.collectAsStateWithLifecycle()
    val apiKey by Secrets.OPEN_SUBSONIC_API_KEY.collectAsStateWithLifecycle()

    val server = openSubsonicName()
    OpenSubsonicSettingsContent(
        enabled = enabled,
        url = url,
        server = server,
        serverCheck = serverCheck,
        allowSelfSigned = allowSelfSigned,
        certificateHost = certificateHost,
        certificate = certificate,
        auth = auth,
        username = username,
        password = password,
        apiKey = apiKey,
        credentialsCheck = credentialsCheck,
        directPlay = directPlay,
        songCacheOff = songCacheSize <= 0,
        replayGainLufs = replayGainLufs,
        syncSongs = syncSongs,
        syncPlaylists = syncPlaylists,
        syncAlbums = syncAlbums,
        syncArtists = syncArtists,
        onRetry = viewModel::retry,
        onTrust = viewModel::trust,
        onForgetCertificate = viewModel::forgetCertificate,
        onSongSyncChange = viewModel::setSongSync,
        onSignIn = onSignIn,
        onBack = onBack,
        modifier = modifier,
        quality = {
            StreamQualitySettings(
                wifi = Preferences.OPEN_SUBSONIC_QUALITY_WIFI,
                mobile = Preferences.OPEN_SUBSONIC_QUALITY_MOBILE,
                label = labelsOf(StreamQuality.entries) { it.openSubsonicLabelRes },
            )
        },
        proxy = {
            ProxySettings(
                source = server,
                enabled = Preferences.OPEN_SUBSONIC_PROXY_ENABLED,
                type = Preferences.OPEN_SUBSONIC_PROXY_TYPE,
                host = Preferences.OPEN_SUBSONIC_PROXY_HOST,
                port = Preferences.OPEN_SUBSONIC_PROXY_PORT,
                username = Secrets.OPEN_SUBSONIC_PROXY_USERNAME,
                password = Secrets.OPEN_SUBSONIC_PROXY_PASSWORD,
            )
        },
    )
}

/**
 * @param server what the server is called, see [openSubsonicName]
 * @param certificate SHA-256 fingerprint of the trusted certificate, empty while there is none
 * @param certificateHost the host [certificate] was trusted for
 * @param onRetry runs the checks again
 * @param onSignIn opens [OpenSubsonicSignInScreen]
 * @param quality how good the streamed songs are, see [StreamQualitySettings]
 * @param proxy the server's proxy, see [ProxySettings]
 */
@Composable
private fun OpenSubsonicSettingsContent(
    enabled: Boolean,
    url: String,
    server: String,
    serverCheck: CheckState,
    allowSelfSigned: Boolean,
    certificateHost: String,
    certificate: String,
    auth: OpenSubsonicAuth,
    username: String,
    password: String,
    apiKey: String,
    credentialsCheck: CheckState,
    directPlay: Boolean,
    songCacheOff: Boolean,
    replayGainLufs: Int,
    syncSongs: Boolean,
    syncPlaylists: Boolean,
    syncAlbums: SyncMode,
    syncArtists: SyncMode,
    onRetry: () -> Unit,
    onTrust: (ServerCheck.CertificateChanged) -> Unit,
    onForgetCertificate: () -> Unit,
    onSongSyncChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    quality: @Composable () -> Unit,
    proxy: @Composable () -> Unit,
) {
    // Fingerprint of the change the user put off, it isn't asked again until they tap the row
    var putOff by rememberSaveable { mutableStateOf<String?>(null) }
    var askForget by remember { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.source_opensubsonicapi), onBack, modifier) {
        MainSwitch(
            title = stringResource(R.string.source_opensubsonicapi),
            summary = stringResource(R.string.setting_description_use_opensubsonic, server),
            checked = enabled,
            onCheckedChange = { Preferences.OPEN_SUBSONIC_ENABLED.update(it) },
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
            serverCheck.serverSummary(server)
        ) {
            if (change != null) putOff = null else onRetry()
        }
        if (change != null && putOff != change.fingerprint)
            CertificateChangedDialog(
                change = change,
                onTrust = { onTrust(change) },
                onDismiss = { putOff = change.fingerprint },
            )

        SwitchSetting(
            title = stringResource(R.string.setting_title_allow_self_signed_certificate),
            summary = stringResource(R.string.setting_description_allow_self_signed_certificate),
            checked = allowSelfSigned,
            onCheckedChange = { Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.update(it) },
        )
        if (allowSelfSigned && certificate.isNotEmpty())
            SettingsItem(
                title = stringResource(R.string.setting_title_trusted_certificate),
                summary = "$certificateHost · ${certificate.shortFingerprint()}",
                onClick = { askForget = true },
            )
        if (askForget)
            ForgetCertificateDialog(
                server = server,
                onForget = {
                    askForget = false
                    onForgetCertificate()
                },
                onDismiss = { askForget = false },
            )

        val credentialsSet = when (auth) {
            OpenSubsonicAuth.PASSWORD -> username.isNotEmpty() && password.isNotEmpty()
            OpenSubsonicAuth.API_KEY -> apiKey.isNotEmpty()
        }
        SettingsItem(
            title = stringResource(R.string.setting_title_sign_in),
            summary = credentialsCheck.signInSummary(
                server = server,
                auth = auth,
                username = username,
                serverSet = url.isNotEmpty(),
                credentialsSet = credentialsSet,
            ),
            onClick = onSignIn,
            trailing = {
                if (credentialsCheck is CheckState.Checking)
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            },
        )

        SettingsSection(stringResource(R.string.setting_header_playback))
        SwitchSetting(
            title = stringResource(R.string.setting_title_direct_play),
            // Without the cache every song streams directly, the setting waits for it
            summary = stringResource(
                if (songCacheOff) R.string.setting_description_direct_play_status_cache_off
                else R.string.setting_description_direct_play,
                server
            ),
            checked = directPlay,
            onCheckedChange = { Preferences.OPEN_SUBSONIC_DIRECT_PLAY.update(it) },
            enabled = !songCacheOff,
        )
        quality()
        NumberSetting(
            title = stringResource(R.string.setting_title_replay_gain_reference),
            value = replayGainLufs.toLong(),
            min = -30,
            max = 0,
            onSave = { Preferences.OPEN_SUBSONIC_REPLAY_GAIN_LUFS.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_replay_gain_reference,
                    stringResource(R.string.lufs, it.toInt()),
                    server,
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_server_library))
        SwitchSetting(
            title = syncTitle(R.plurals.unit_song),
            summary = syncSongsDescription(syncSongs, server),
            checked = syncSongs,
            onCheckedChange = onSongSyncChange,
        )
        SwitchSetting(
            title = syncTitle(R.plurals.unit_playlist),
            summary = syncPlaylistsDescription(syncPlaylists, server),
            checked = syncPlaylists,
            onCheckedChange = { Preferences.OPEN_SUBSONIC_SYNC_PLAYLISTS.update(it) },
        )
        ChoiceSetting(
            title = syncTitle(R.plurals.unit_album),
            value = syncAlbums,
            options = SyncMode.entries,
            label = labelsOf(SyncMode.entries) { it.labelRes },
            onSelect = { Preferences.OPEN_SUBSONIC_SYNC_ALBUMS.update(it) },
            summary = syncModeDescription(syncAlbums, R.plurals.unit_album),
        )
        ChoiceSetting(
            title = syncTitle(R.plurals.unit_artist),
            value = syncArtists,
            options = SyncMode.entries,
            label = labelsOf(SyncMode.entries) { it.labelRes },
            onSelect = { Preferences.OPEN_SUBSONIC_SYNC_ARTISTS.update(it) },
            summary = syncModeDescription(syncArtists, R.plurals.unit_artist),
        )

        proxy()
    }
}

@ScreenPreviews
@Composable
private fun OpenSubsonicSettingsPreview() = PreviewTheme { OpenSubsonicSettingsSample(false) }

/** The song cache is off, Direct play is disabled. */
@ScreenPreviews
@Composable
private fun OpenSubsonicSettingsCacheOffPreview() = PreviewTheme {
    OpenSubsonicSettingsSample(songCacheOff = true)
}

@Composable
private fun OpenSubsonicSettingsSample(songCacheOff: Boolean) {
    val online = CheckState.Done(
        ServerCheck.Success(ServerInfo("1.16.1", "navidrome", "0.58.0", isOpenSubsonic = true))
    )
    OpenSubsonicSettingsContent(
        enabled = true,
        url = "https://music.example.com",
        server = "Navidrome",
        serverCheck = online,
        allowSelfSigned = false,
        certificateHost = "",
        certificate = "",
        auth = OpenSubsonicAuth.PASSWORD,
        username = "neon",
        password = "secret",
        apiKey = "",
        credentialsCheck = online,
        directPlay = false,
        songCacheOff = songCacheOff,
        replayGainLufs = -18,
        syncSongs = true,
        syncPlaylists = true,
        syncAlbums = SyncMode.ALL,
        syncArtists = SyncMode.ALL,
        onRetry = {},
        onTrust = {},
        onForgetCertificate = {},
        onSongSyncChange = {},
        onSignIn = {},
        onBack = {},
        quality = {
            StreamQualitySettings(
                wifi = StreamQuality.ORIGINAL,
                mobile = StreamQuality.HIGH,
                label = labelsOf(StreamQuality.entries) { it.openSubsonicLabelRes },
                onWifiChange = {},
                onMobileChange = {},
            )
        },
        proxy = { PreviewProxySettings("Navidrome") },
    )
}
