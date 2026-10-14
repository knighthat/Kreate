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

package me.knighthat.kreate3.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.youtube.YouTubeChannel
import me.knighthat.kreate3.provider.youtube.youTubeSyncSetting
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

private const val NEXT_LAUNCH = "Applies from the next launch"

/** What the ping found, for the row under the address. */
private fun CheckState.serverSummary(): String? = when (this) {
    CheckState.Idle -> null
    CheckState.Checking -> "Checking the server…"
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> listOfNotNull(
            "Online",
            result.server.type?.replaceFirstChar { it.uppercase() },
            result.server.serverVersion,
        ).joinToString(" · ")

        is ServerCheck.Unreachable -> "Can't reach the server: ${result.reason}"
        is ServerCheck.UntrustedCertificate ->
            "The server's certificate isn't trusted. If the server uses its own, allow self-signed certificates"

        is ServerCheck.CertificateChanged -> "The server's certificate changed. Tap to review it"
        ServerCheck.NotSubsonic -> "This address is not an OpenSubsonic server"
        // Not checked, the row is hidden for these
        ServerCheck.Disabled, ServerCheck.NotConfigured, is ServerCheck.Rejected -> null
    }
}

/** What the credential check found, for the row under the credentials. */
private fun CheckState.credentialsSummary(): String? = when (this) {
    CheckState.Idle -> null
    CheckState.Checking -> "Signing in…"
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> "Signed in"
        is ServerCheck.Rejected -> when (result.code) {
            40 -> "Wrong username or password"
            44 -> "Invalid API key"
            else -> result.message.ifEmpty { "The server refused to sign in (${result.code})" }
        }

        is ServerCheck.Unreachable -> "Can't reach the server: ${result.reason}"
        is ServerCheck.UntrustedCertificate -> "The server's certificate isn't trusted"
        is ServerCheck.CertificateChanged -> "The server's certificate changed"
        ServerCheck.NotSubsonic -> "This address is not an OpenSubsonic server"
        ServerCheck.Disabled, ServerCheck.NotConfigured -> null
    }
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
        title = { Text("Certificate changed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${change.host} presents a different self-signed certificate than the one you " +
                            "trusted. If you didn't replace it, someone may be pretending to be your server."
                )
                Fingerprint("Trusted until now", change.pinnedFingerprint)
                Fingerprint("Presented now", change.fingerprint)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text("Trust new certificate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
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
        title = { Text("Forget certificate?") },
        text = { Text("The next certificate the server presents will be trusted without asking.") },
        confirmButton = { TextButton(onClick = onForget) { Text("Forget") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
 * @return the reason the address is not acceptable, `null` when it is
 */
private fun validateServerUrl(url: String): String? = when {
    url.isEmpty() -> null
    !url.startsWith("https://") && !url.startsWith("http://") -> "Start with https:// or http://"
    url.substringAfter("://").isBlank() -> "Add the host of the server"
    else -> null
}

/**
 * How requests are made, and the OpenSubsonic server (like Navidrome) to play music from.
 */
/**
 * Who is signed in to YouTube, with a way to sign in or out.
 */
@Composable
private fun YouTubeAccountSettings(
    state: YouTubeAccountState,
    channels: List<YouTubeChannel>,
    pageId: String,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSwitch: (YouTubeChannel) -> Unit,
    onSyncChange: (LibrarySection, Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    var askSignOut by remember { mutableStateOf(false) }
    var pickChannel by remember { mutableStateOf(false) }
    // A choice only when the account has several channels
    val canSwitch = channels.size > 1

    SettingsSection("YouTube")
    when (state) {
        YouTubeAccountState.SignedOut -> SettingsItem(
            title = "Sign in",
            summary = "Brings your albums, artists and playlists, and makes suggestions yours",
            onClick = onSignIn,
        )

        YouTubeAccountState.Checking -> SettingsItem(title = "Account", summary = "Checking…")
        is YouTubeAccountState.SignedIn -> SettingsItem(
            title = state.account.name,
            summary = state.account.handle ?: "Signed in",
            onClick = if (canSwitch) ({ pickChannel = true }) else null,
            trailing = if (canSwitch) ({
                Icon(
                    painterResource(R.drawable.chevron_right),
                    contentDescription = "Switch channel"
                )
            }) else null,
        )

        YouTubeAccountState.Ended -> SettingsItem(
            title = "Signed out by YouTube",
            summary = "The session ended, tap to sign in again",
            onClick = onSignIn,
        )

        is YouTubeAccountState.Failed -> SettingsItem(
            title = "Account",
            summary = "${state.reason}, tap to try again",
            onClick = onRetry,
        )
    }
    if (state != YouTubeAccountState.SignedOut) {
        LibrarySection.entries.forEach { section ->
            val syncs by section.youTubeSyncSetting.collectAsStateWithLifecycle()
            val (title, summary) = when (section) {
                LibrarySection.SONGS -> "Sync liked songs" to "The songs you liked are liked here too"
                LibrarySection.ALBUMS -> "Sync albums" to "The albums you saved show in your library"
                LibrarySection.ARTISTS -> "Sync artists" to "The artists you subscribed to show in your library"
                LibrarySection.PLAYLISTS -> "Sync playlists" to "Your YouTube playlists show in your library"
            }
            SwitchSetting(
                title = title,
                summary = summary,
                checked = syncs,
                onCheckedChange = { onSyncChange(section, it) },
            )
        }
        SettingsItem(
            title = "Sign out",
            summary = "Your YouTube library leaves this profile",
            onClick = { askSignOut = true },
        )
    }
    if (pickChannel && canSwitch)
        YouTubeChannelPicker(
            channels = channels,
            pageId = pageId,
            onPick = {
                pickChannel = false
                onSwitch(it)
            },
            onDismiss = { pickChannel = false },
        )
    if (askSignOut)
        AlertDialog(
            onDismissRequest = { askSignOut = false },
            title = { Text("Sign out of YouTube?") },
            text = { Text("Your YouTube albums, artists and playlists leave this profile. You can sign in again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    askSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { askSignOut = false }) { Text("Cancel") } },
        )
}

@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit,
    onSignInToYouTube: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkSettingsViewModel = koinViewModel(),
    youtube: YouTubeAccountViewModel = koinViewModel(),
) {
    val youtubeAccount by youtube.account.collectFlowAsState()
    val youtubeChannels by youtube.channels.collectFlowAsState()
    val youtubePageId by youtube.pageId.collectFlowAsState()
    val serverCheck by viewModel.server.collectFlowAsState()
    val allowSelfSigned by Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.collectAsStateWithLifecycle()
    val certificateHost by Preferences.OPEN_SUBSONIC_CERTIFICATE_HOST.collectAsStateWithLifecycle()
    val certificate by Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256.collectAsStateWithLifecycle()
    // Fingerprint of the change the user put off, it isn't asked again until they tap the row
    var putOff by rememberSaveable { mutableStateOf<String?>(null) }
    var askForget by remember { mutableStateOf(false) }
    val credentialsCheck by viewModel.credentials.collectFlowAsState()
    val connectTimeout by Preferences.NETWORK_CONNECT_TIMEOUT.collectAsStateWithLifecycle()
    val readTimeout by Preferences.NETWORK_READ_TIMEOUT.collectAsStateWithLifecycle()
    val maxRetries by Preferences.NETWORK_MAX_RETRIES.collectAsStateWithLifecycle()
    val enabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val auth by Preferences.OPEN_SUBSONIC_AUTH.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val username by Preferences.OPEN_SUBSONIC_USERNAME.collectAsStateWithLifecycle()
    val password by Secrets.OPEN_SUBSONIC_PASSWORD.collectAsStateWithLifecycle()
    val apiKey by Secrets.OPEN_SUBSONIC_API_KEY.collectAsStateWithLifecycle()

    SettingsPage("Network", onBack, modifier) {
        SettingsSection("Requests")
        NumberSetting(
            title = "Connect timeout",
            value = connectTimeout.toLong(),
            unit = "seconds",
            min = 1,
            max = 120,
            onSave = { Preferences.NETWORK_CONNECT_TIMEOUT.update(it.toInt()) },
            note = NEXT_LAUNCH,
        )
        NumberSetting(
            title = "Read timeout",
            value = readTimeout.toLong(),
            unit = "seconds",
            min = 1,
            max = 300,
            onSave = { Preferences.NETWORK_READ_TIMEOUT.update(it.toInt()) },
            note = NEXT_LAUNCH,
        )
        NumberSetting(
            title = "Retries",
            value = maxRetries.toLong(),
            unit = "times",
            min = 0,
            max = 10,
            onSave = { Preferences.NETWORK_MAX_RETRIES.update(it.toInt()) },
            note = "Requests that got a server error are sent again. $NEXT_LAUNCH",
        )

        YouTubeAccountSettings(
            state = youtubeAccount,
            channels = youtubeChannels,
            pageId = youtubePageId,
            onSignIn = onSignInToYouTube,
            onRetry = youtube::retry,
            onSwitch = youtube::switchTo,
            onSyncChange = youtube::setSyncing,
            onSignOut = youtube::signOut,
        )

        SettingsSection("OpenSubsonic")
        SwitchSetting(
            title = "Use OpenSubsonic",
            summary = "The server is not contacted while this is off",
            checked = enabled,
            onCheckedChange = Preferences.OPEN_SUBSONIC_ENABLED::update,
        )

        // Saved values stay when hidden, they are used again once the setting is back on
        if (!enabled) return@SettingsPage

        TextSetting(
            title = "Server address",
            value = url,
            placeholder = "https://music.example.com",
            keyboardType = KeyboardType.Uri,
            validate = ::validateServerUrl,
            onSave = { Preferences.OPEN_SUBSONIC_URL.update(it.trim().trimEnd('/')) },
        )
        val change = (serverCheck as? CheckState.Done)?.result as? ServerCheck.CertificateChanged
        CheckRow("Server", serverCheck, serverCheck.serverSummary()) {
            if (change != null) putOff = null else viewModel.retry()
        }
        if (change != null && putOff != change.fingerprint)
            CertificateChangedDialog(
                change = change,
                onTrust = { viewModel.trust(change) },
                onDismiss = { putOff = change.fingerprint },
            )

        SwitchSetting(
            title = "Allow self-signed certificate",
            summary = "Trusts the server's own certificate the first time, and asks if it changes",
            checked = allowSelfSigned,
            onCheckedChange = Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED::update,
        )
        if (allowSelfSigned && certificate.isNotEmpty())
            SettingsItem(
                title = "Trusted certificate",
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

        SettingsSection("Sign in with")
        RadioSetting(
            value = auth,
            options = OpenSubsonicAuth.entries,
            label = { if (it == OpenSubsonicAuth.PASSWORD) "Username and password" else "API key" },
            onSelect = Preferences.OPEN_SUBSONIC_AUTH::update,
        )
        when (auth) {
            OpenSubsonicAuth.PASSWORD -> {
                TextSetting(
                    title = "Username",
                    value = username,
                    onSave = { Preferences.OPEN_SUBSONIC_USERNAME.update(it.trim()) },
                )
                TextSetting(
                    title = "Password",
                    value = password,
                    summary = if (password.isEmpty()) "" else "•".repeat(8),
                    hidden = true,
                    onSave = Secrets.OPEN_SUBSONIC_PASSWORD::update,
                )
            }

            OpenSubsonicAuth.API_KEY -> TextSetting(
                title = "API key",
                value = apiKey,
                summary = if (apiKey.isEmpty()) "" else "•".repeat(8),
                hidden = true,
                onSave = Secrets.OPEN_SUBSONIC_API_KEY::update,
            )
        }
        CheckRow(
            "Sign in",
            credentialsCheck,
            credentialsCheck.credentialsSummary(),
            viewModel::retry
        )
    }
}
