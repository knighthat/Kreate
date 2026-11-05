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
import me.knighthat.kreate3.provider.opensubsonic.CheckState
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicServer
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerInfo
import me.knighthat.kreate3.provider.youtube.YouTubeAccount
import me.knighthat.kreate3.provider.youtube.YouTubeAccountState
import me.knighthat.kreate3.provider.youtube.YouTubeAccountStore
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.openSubsonicName
import me.knighthat.kreate3.ui.navigation.OpenSubsonicSettings
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.YouTubeSettings
import me.knighthat.kreate3.ui.screen.settings.CountSetting
import me.knighthat.kreate3.ui.screen.settings.PreviewProxySettings
import me.knighthat.kreate3.ui.screen.settings.ProxySettings
import me.knighthat.kreate3.ui.screen.settings.SecondSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** Who is signed in, for the row that opens the YouTube page. */
@Composable
private fun YouTubeAccountState.summary(): String = when (this) {
    YouTubeAccountState.SignedOut -> stringResource(R.string.setting_description_configure_youtube_status_not_signed_in)
    YouTubeAccountState.Checking -> stringResource(R.string.status_checking)
    is YouTubeAccountState.SignedIn ->
        stringResource(
            R.string.setting_description_configure_youtube_status_signed_in_as,
            account.name
        )

    YouTubeAccountState.Ended -> stringResource(
        R.string.setting_description_configure_youtube_status_signed_out,
        stringResource(R.string.source_ytm)
    )
    is YouTubeAccountState.Failed -> reason ?: stringResource(
        R.string.setting_description_account_status_unreachable,
        stringResource(R.string.source_ytm)
    )
}

/**
 * The server's name and version, or why they are unknown, for the row that opens its page.
 *
 * @param server what the server is called, see [openSubsonicName]
 */
@Composable
private fun CheckState.serverName(server: String): String = when (this) {
    CheckState.Idle, CheckState.Checking ->
        stringResource(R.string.setting_description_server_status_checking, server)

    is CheckState.Done -> when (val result = result) {
        // Without the build tag, the server's page has the version as it said it
        is ServerCheck.Success -> listOfNotNull(result.server.name, result.server.version)
            .joinToString(" · ")
            .ifEmpty { stringResource(R.string.status_online) }

        is ServerCheck.Unreachable -> stringResource(
            R.string.setting_description_configure_opensubsonic_status_unreachable,
            server
        )

        is ServerCheck.UntrustedCertificate -> stringResource(
            R.string.setting_description_configure_opensubsonic_status_certificate_untrusted,
            server
        )

        is ServerCheck.CertificateChanged -> stringResource(
            R.string.setting_description_configure_opensubsonic_status_certificate_changed,
            server
        )

        ServerCheck.NotSubsonic -> stringResource(
            R.string.setting_description_configure_opensubsonic_status_not_opensubsonic,
            stringResource(R.string.source_opensubsonic)
        )
        // Not what a ping returns
        ServerCheck.Disabled, ServerCheck.NotConfigured, is ServerCheck.Rejected ->
            stringResource(R.string.status_online)
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
    youtube: YouTubeAccountStore = koinInject(),
) {
    // What the app found out when it started and after each change, nothing is asked here
    val server by OpenSubsonicServer.server.collectFlowAsState()
    val youtubeAccount by youtube.state.collectFlowAsState()
    val youtubeEnabled by Preferences.YOUTUBE_ACCOUNT_ENABLED.collectAsStateWithLifecycle()
    val openSubsonicEnabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val connectTimeout by Preferences.NETWORK_CONNECT_TIMEOUT.collectAsStateWithLifecycle()
    val readTimeout by Preferences.NETWORK_READ_TIMEOUT.collectAsStateWithLifecycle()
    val maxRetries by Preferences.NETWORK_MAX_RETRIES.collectAsStateWithLifecycle()

    NetworkSettingsContent(
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        maxRetries = maxRetries,
        youtubeEnabled = youtubeEnabled,
        youtubeAccount = youtubeAccount,
        openSubsonicEnabled = openSubsonicEnabled,
        url = url,
        server = server,
        serverName = openSubsonicName(),
        onBack = onBack,
        onOpen = onOpen,
        modifier = modifier,
        // A source's own proxy, on its page, overrides this one
        proxy = {
            ProxySettings(
                source = null,
                enabled = Preferences.NETWORK_PROXY_ENABLED,
                type = Preferences.NETWORK_PROXY_TYPE,
                host = Preferences.NETWORK_PROXY_HOST,
                port = Preferences.NETWORK_PROXY_PORT,
                username = Secrets.NETWORK_PROXY_USERNAME,
                password = Secrets.NETWORK_PROXY_PASSWORD,
            )
        },
    )
}

/**
 * @param url the OpenSubsonic server's address, empty while there is none
 * @param serverName what the server is called, see [openSubsonicName]
 * @param proxy the app's proxy, see [ProxySettings]
 */
@Composable
private fun NetworkSettingsContent(
    connectTimeout: Int,
    readTimeout: Int,
    maxRetries: Int,
    youtubeEnabled: Boolean,
    youtubeAccount: YouTubeAccountState,
    openSubsonicEnabled: Boolean,
    url: String,
    server: CheckState,
    serverName: String,
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    proxy: @Composable () -> Unit,
) {
    SettingsPage(
        stringResource(R.string.screen_title_network),
        onBack,
        modifier,
        restartApp = true
    ) {
        SettingsSection(stringResource(R.string.setting_header_requests))
        SecondSetting(
            title = stringResource(R.string.setting_title_connect_timeout),
            value = connectTimeout,
            range = 1..120,
            summary = stringResource(R.string.setting_description_connect_timeout),
            onSelect = { Preferences.NETWORK_CONNECT_TIMEOUT.update(it) },
        )
        SecondSetting(
            title = stringResource(R.string.setting_title_read_timeout),
            value = readTimeout,
            range = 1..300,
            summary = stringResource(R.string.setting_description_read_timeout),
            onSelect = { Preferences.NETWORK_READ_TIMEOUT.update(it) },
        )
        CountSetting(
            title = stringResource(R.string.setting_title_retries),
            value = maxRetries,
            range = 0..10,
            unit = { pluralStringResource(R.plurals.unit_time, it) },
            summary = stringResource(R.string.setting_description_retries),
            onSelect = { Preferences.NETWORK_MAX_RETRIES.update(it) },
        )

        proxy()

        SettingsSection(stringResource(R.string.setting_header_sources))
        SettingsItem(
            title = stringResource(
                R.string.setting_title_configure_source,
                stringResource(R.string.source_ytm)
            ),
            summary = if (youtubeEnabled) youtubeAccount.summary() else stringResource(R.string.status_off),
            onClick = { onOpen(YouTubeSettings) },
        )
        SettingsItem(
            title = stringResource(
                R.string.setting_title_configure_source,
                stringResource(R.string.source_opensubsonic)
            ),
            summary = when {
                !openSubsonicEnabled -> stringResource(R.string.status_off)
                url.isEmpty() -> stringResource(R.string.setting_description_configure_opensubsonic_status_no_server)
                else -> server.serverName(serverName)
            },
            onClick = { onOpen(OpenSubsonicSettings) },
        )
    }
}

@ScreenPreviews
@Composable
private fun NetworkSettingsPreview() = PreviewTheme {
    NetworkSettingsContent(
        connectTimeout = 15,
        readTimeout = 30,
        maxRetries = 3,
        youtubeEnabled = true,
        youtubeAccount = YouTubeAccountState.SignedIn(
            YouTubeAccount(
                "Neon Coast",
                "@neoncoast",
                null
            )
        ),
        openSubsonicEnabled = true,
        url = "https://music.example.com",
        server = CheckState.Done(
            ServerCheck.Success(ServerInfo("1.16.1", "navidrome", "0.58.0", isOpenSubsonic = true))
        ),
        serverName = "Navidrome",
        onBack = {},
        onOpen = {},
        proxy = { PreviewProxySettings(source = null) },
    )
}
