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

package me.knighthat.kreate3.ui.screen.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.ui.navigation.OpenSubsonicSettings
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.YouTubeSettings
import me.knighthat.kreate3.ui.screen.settings.NumberSetting
import me.knighthat.kreate3.ui.screen.settings.ProxySettings
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.network.opensubsonic.CheckState
import me.knighthat.kreate3.ui.screen.settings.network.opensubsonic.OpenSubsonicSettingsViewModel
import me.knighthat.kreate3.ui.screen.settings.network.youtube.YouTubeAccountState
import me.knighthat.kreate3.ui.screen.settings.network.youtube.YouTubeAccountViewModel
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** Who is signed in, for the row that opens the YouTube page. */
@Composable
private fun YouTubeAccountState.summary(): String = when (this) {
    YouTubeAccountState.SignedOut -> stringResource(R.string.setting_description_configure_youtube_status_not_signed_in)
    YouTubeAccountState.Checking -> stringResource(R.string.setting_description_configure_youtube_status_checking)
    is YouTubeAccountState.SignedIn ->
        stringResource(
            R.string.setting_description_configure_youtube_status_signed_in_as,
            account.name
        )

    YouTubeAccountState.Ended -> stringResource(
        R.string.setting_description_configure_youtube_status_signed_out,
        stringResource(R.string.source_ytm)
    )
    is YouTubeAccountState.Failed -> reason
}

/** The server's name and version, or why they are unknown, for the row that opens its page. */
@Composable
private fun CheckState.serverName(): String = when (this) {
    CheckState.Idle, CheckState.Checking -> stringResource(R.string.setting_description_configure_opensubsonic_status_checking)
    is CheckState.Done -> when (val result = result) {
        is ServerCheck.Success -> listOfNotNull(
            result.server.type?.replaceFirstChar { it.uppercase() },
            result.server.serverVersion,
        ).joinToString(" ")
            .ifEmpty { stringResource(R.string.setting_description_configure_opensubsonic_status_online) }

        is ServerCheck.Unreachable -> stringResource(R.string.setting_description_configure_opensubsonic_status_unreachable)
        is ServerCheck.UntrustedCertificate -> stringResource(R.string.setting_description_configure_opensubsonic_status_certificate_untrusted)
        is ServerCheck.CertificateChanged -> stringResource(R.string.setting_description_configure_opensubsonic_status_certificate_changed)
        ServerCheck.NotSubsonic -> stringResource(
            R.string.setting_description_configure_opensubsonic_status_not_opensubsonic,
            stringResource(R.string.source_opensubsonicapi)
        )
        // Not what a ping returns
        ServerCheck.Disabled, ServerCheck.NotConfigured, is ServerCheck.Rejected ->
            stringResource(R.string.setting_description_configure_opensubsonic_status_online)
    }
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
        SettingsSection(stringResource(R.string.setting_header_requests))
        NumberSetting(
            title = stringResource(R.string.setting_title_connect_timeout),
            value = connectTimeout.toLong(),
            unit = pluralStringResource(R.plurals.unit_second, connectTimeout),
            min = 1,
            max = 120,
            onSave = { Preferences.NETWORK_CONNECT_TIMEOUT.update(it.toInt()) },
            note = stringResource(R.string.setting_description_next_launch),
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_read_timeout),
            value = readTimeout.toLong(),
            unit = pluralStringResource(R.plurals.unit_second, readTimeout),
            min = 1,
            max = 300,
            onSave = { Preferences.NETWORK_READ_TIMEOUT.update(it.toInt()) },
            note = stringResource(R.string.setting_description_next_launch),
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_retries),
            value = maxRetries.toLong(),
            unit = pluralStringResource(R.plurals.unit_time, maxRetries),
            min = 0,
            max = 10,
            onSave = { Preferences.NETWORK_MAX_RETRIES.update(it.toInt()) },
            note = stringResource(R.string.setting_description_retries),
        )

        // A source's own proxy, on its page, overrides this one
        ProxySettings(
            source = null,
            enabled = Preferences.NETWORK_PROXY_ENABLED,
            type = Preferences.NETWORK_PROXY_TYPE,
            host = Preferences.NETWORK_PROXY_HOST,
            port = Preferences.NETWORK_PROXY_PORT,
            username = Secrets.NETWORK_PROXY_USERNAME,
            password = Secrets.NETWORK_PROXY_PASSWORD,
        )

        SettingsSection(stringResource(R.string.setting_header_sources))
        SettingsItem(
            title = stringResource(
                R.string.setting_title_configure_youtube,
                stringResource(R.string.source_ytm)
            ),
            summary = if (youtubeEnabled) youtubeAccount.summary() else "Off",
            onClick = { onOpen(YouTubeSettings) },
        )
        SettingsItem(
            title = stringResource(
                R.string.setting_title_configure_opensubsonic,
                stringResource(R.string.source_opensubsonicapi)
            ),
            summary = when {
                !openSubsonicEnabled -> "Off"
                url.isEmpty() -> stringResource(R.string.setting_description_configure_opensubsonic_status_no_server)
                else -> server.serverName()
            },
            onClick = { onOpen(OpenSubsonicSettings) },
        )
    }
}
