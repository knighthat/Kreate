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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.ui.navigation.OpenSubsonicSettings
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.YouTubeSettings
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

private const val NEXT_LAUNCH = "Applies from the next launch"

/** Who is signed in, for the row that opens the YouTube page. */
private fun YouTubeAccountState.summary(): String = when (this) {
    YouTubeAccountState.SignedOut -> "Not signed in"
    YouTubeAccountState.Checking -> "Checking…"
    is YouTubeAccountState.SignedIn -> "Signed in as ${account.name}"
    YouTubeAccountState.Ended -> "Signed out by YouTube"
    is YouTubeAccountState.Failed -> reason
}

/** The server's name and version, or why they are unknown, for the row that opens its page. */
private fun CheckState.serverName(): String = when (this) {
    CheckState.Idle, CheckState.Checking -> "Checking the server…"
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> listOfNotNull(
            result.server.type?.replaceFirstChar { it.uppercase() },
            result.server.serverVersion,
        ).joinToString(" ").ifEmpty { "Online" }

        is ServerCheck.Unreachable -> "Can't reach the server"
        is ServerCheck.UntrustedCertificate -> "The server's certificate isn't trusted"
        is ServerCheck.CertificateChanged -> "The server's certificate changed"
        ServerCheck.NotSubsonic -> "Not an OpenSubsonic server"
        // Not what a ping returns
        ServerCheck.Disabled, ServerCheck.NotConfigured, is ServerCheck.Rejected -> "Online"
    }
}

/**
 * A switch for each [LibrarySection] of a source: the liked songs are synced, the others show.
 *
 * @param setting the setting of a section, like `youTubeSyncSetting`
 * @param where what the user's library is called on the source, like "on YouTube"
 */
@Composable
internal fun LibrarySyncSettings(
    setting: (LibrarySection) -> Preference<Boolean>,
    where: String,
    onSyncChange: (LibrarySection, Boolean) -> Unit,
) = LibrarySection.entries.forEach { section ->
    val syncs by setting(section).collectAsStateWithLifecycle()
    val (title, summary) = when (section) {
        LibrarySection.SONGS -> "Sync liked songs" to "The songs you liked $where are liked here too"
        LibrarySection.ALBUMS -> "Show albums" to "The albums you saved $where show in your library"
        LibrarySection.ARTISTS -> "Show artists" to "The artists you follow $where show in your library"
        LibrarySection.PLAYLISTS -> "Show playlists" to "Your playlists $where show in your library"
    }
    SwitchSetting(
        title = title,
        summary = summary,
        checked = syncs,
        onCheckedChange = { onSyncChange(section, it) },
    )
}

/**
 * How requests are sent, with a page for each source to set up.
 *
 * @param onOpen called with the route of the source's page
 */
@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    youtube: YouTubeAccountViewModel = koinViewModel(),
    openSubsonic: OpenSubsonicSettingsViewModel = koinViewModel(),
) {
    val server by openSubsonic.server.collectFlowAsState()
    val youtubeAccount by youtube.account.collectFlowAsState()
    val youtubeEnabled by Preferences.YOUTUBE_ACCOUNT_ENABLED.collectAsStateWithLifecycle()
    val openSubsonicEnabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val connectTimeout by Preferences.NETWORK_CONNECT_TIMEOUT.collectAsStateWithLifecycle()
    val readTimeout by Preferences.NETWORK_READ_TIMEOUT.collectAsStateWithLifecycle()
    val maxRetries by Preferences.NETWORK_MAX_RETRIES.collectAsStateWithLifecycle()

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

        SettingsSection("Sources")
        SettingsItem(
            title = "Configure YouTube",
            summary = if (youtubeEnabled) youtubeAccount.summary() else "Off",
            onClick = { onOpen(YouTubeSettings) },
        )
        SettingsItem(
            title = "Configure OpenSubsonic",
            summary = when {
                !openSubsonicEnabled -> "Off"
                url.isEmpty() -> "No server set"
                else -> server.serverName()
            },
            onClick = { onOpen(OpenSubsonicSettings) },
        )
    }
}
