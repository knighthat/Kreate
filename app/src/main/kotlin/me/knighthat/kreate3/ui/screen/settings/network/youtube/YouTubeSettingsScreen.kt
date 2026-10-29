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

package me.knighthat.kreate3.ui.screen.settings.network.youtube

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.youtube.YOUTUBE_REGIONS
import me.knighthat.kreate3.provider.youtube.YouTubeAccount
import me.knighthat.kreate3.provider.youtube.YouTubeChannel
import me.knighthat.kreate3.provider.youtube.youTubeSyncSetting
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.component.youTubeLabelRes
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.LibrarySyncSettings
import me.knighthat.kreate3.ui.screen.settings.MainSwitch
import me.knighthat.kreate3.ui.screen.settings.PreviewProxySettings
import me.knighthat.kreate3.ui.screen.settings.ProxySettings
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.StreamQualitySettings
import org.koin.androidx.compose.koinViewModel
import java.text.Collator
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/**
 * Whether the YouTube account is used, who is signed in, with a way to sign in or out, the region
 * of the catalog, how good the songs streamed are and the proxy. Only the switch, the region, the
 * quality and the proxy show while the account is off.
 */
@Composable
fun YouTubeSettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeAccountViewModel = koinViewModel(),
) {
    val account by viewModel.account.collectFlowAsState()
    val channels by viewModel.channels.collectFlowAsState()
    val pageId by viewModel.pageId.collectFlowAsState()
    val enabled by Preferences.YOUTUBE_ACCOUNT_ENABLED.collectAsStateWithLifecycle()
    val region by Preferences.YOUTUBE_REGION.collectAsStateWithLifecycle()

    YouTubeSettingsContent(
        enabled = enabled,
        account = account,
        channels = channels,
        pageId = pageId,
        region = region,
        onEnabledChange = viewModel::setEnabled,
        onSignIn = onSignIn,
        onRetry = viewModel::retry,
        onSignOut = viewModel::signOut,
        onSwitchChannel = viewModel::switchTo,
        onBack = onBack,
        modifier = modifier,
        library = { LibrarySyncSettings({ it.youTubeSyncSetting }, viewModel::setSyncing) },
        quality = { YouTubeQualitySettings(it) },
        // Signed out requests go through it too
        proxy = {
            ProxySettings(
                source = stringResource(R.string.source_ytm),
                enabled = Preferences.YOUTUBE_PROXY_ENABLED,
                type = Preferences.YOUTUBE_PROXY_TYPE,
                host = Preferences.YOUTUBE_PROXY_HOST,
                port = Preferences.YOUTUBE_PROXY_PORT,
                username = Secrets.YOUTUBE_PROXY_USERNAME,
                password = Secrets.YOUTUBE_PROXY_PASSWORD,
            )
        },
    )
}

/**
 * @param pageId page id of the channel acted as now, empty for the account's own
 * @param region country code of the catalog, empty for the device's region
 * @param library what of the account's library is synced, see [LibrarySyncSettings]
 * @param quality the stream quality, with Original while someone is signed in
 * @param proxy YouTube's proxy, see [ProxySettings]
 */
@Composable
private fun YouTubeSettingsContent(
    enabled: Boolean,
    account: YouTubeAccountState,
    channels: List<YouTubeChannel>,
    pageId: String,
    region: String,
    onEnabledChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchChannel: (YouTubeChannel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    library: @Composable () -> Unit,
    quality: @Composable (signedIn: Boolean) -> Unit,
    proxy: @Composable () -> Unit,
) {
    var askSignOut by remember { mutableStateOf(false) }
    var pickChannel by remember { mutableStateOf(false) }
    // A choice only when the account has several channels
    val canSwitch = channels.size > 1

    SettingsPage(stringResource(R.string.source_ytm), onBack, modifier) {
        MainSwitch(
            title = stringResource(
                R.string.setting_title_use_youtube_account,
                stringResource(R.string.source_ytm)
            ),
            summary = stringResource(
                R.string.setting_description_use_youtube_account,
                stringResource(R.string.source_ytm)
            ),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )

        // The session stays when hidden, it is used again once the setting is back on
        if (enabled) {

            SettingsSection(stringResource(R.string.setting_title_account))
            when (val state = account) {
                YouTubeAccountState.SignedOut -> SettingsItem(
                    title = stringResource(R.string.setting_title_sign_in),
                    summary = stringResource(R.string.setting_description_sign_in_youtube),
                    onClick = onSignIn,
                )

                YouTubeAccountState.Checking ->
                    SettingsItem(
                        title = stringResource(R.string.setting_title_account),
                        summary = stringResource(R.string.setting_description_account_status_checking)
                    )

                is YouTubeAccountState.SignedIn -> SettingsItem(
                    title = state.account.name,
                    summary = state.account.handle
                        ?: stringResource(R.string.setting_description_account_status_signed_in),
                    onClick = if (canSwitch) ({ pickChannel = true }) else null,
                    trailing = if (canSwitch) ({
                        Icon(
                            painterResource(R.drawable.chevron_right),
                            contentDescription = stringResource(R.string.semantic_switch_channel)
                        )
                    }) else null,
                )

                YouTubeAccountState.Ended -> SettingsItem(
                    title = stringResource(
                        R.string.setting_title_account_status_signed_out,
                        stringResource(R.string.source_ytm)
                    ),
                    summary = stringResource(R.string.setting_description_account_status_session_ended),
                    onClick = onSignIn,
                )

                is YouTubeAccountState.Failed -> SettingsItem(
                    title = stringResource(R.string.setting_title_account),
                    summary = stringResource(
                        R.string.setting_description_account_status_failed,
                        state.reason
                    ),
                    onClick = onRetry,
                )
            }
            if (account != YouTubeAccountState.SignedOut) {
                SettingsItem(
                    title = stringResource(R.string.setting_title_sign_out),
                    summary = stringResource(R.string.setting_description_sign_out),
                    onClick = { askSignOut = true },
                )

                SettingsSection(stringResource(R.string.setting_header_library))
                library()
            }
            if (pickChannel && canSwitch)
                YouTubeChannelPicker(
                    channels = channels,
                    pageId = pageId,
                    onPick = {
                        pickChannel = false
                        onSwitchChannel(it)
                    },
                    onDismiss = { pickChannel = false },
                )
            if (askSignOut)
                AlertDialog(
                    onDismissRequest = { askSignOut = false },
                    title = {
                        Text(
                            stringResource(
                                R.string.dialog_title_sign_out_of_youtube,
                                stringResource(R.string.source_ytm)
                            )
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                R.string.dialog_body_sign_out_of_youtube,
                                stringResource(R.string.source_ytm)
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            askSignOut = false
                            onSignOut()
                        }) { Text(stringResource(R.string.action_sign_out)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            askSignOut = false
                        }) { Text(stringResource(R.string.action_cancel)) }
                    },
                )
        }

        // Signed out requests are sent with it too
        SettingsSection(stringResource(R.string.setting_title_region))
        RegionSetting(region)

        // YouTube plays signed out too, only Original needs the account
        SettingsSection(stringResource(R.string.setting_header_playback))
        quality(enabled && account is YouTubeAccountState.SignedIn)

        proxy()
    }
}

/**
 * The country of the catalog, each named in the app's language and sorted by that name, after
 * System (empty) which follows the device.
 */
@Composable
private fun RegionSetting(region: String) {
    val locale = LocalResources.current.configuration.locales[0]
    val regions = remember(locale) {
        val collator = Collator.getInstance(locale)
        listOf("") + YOUTUBE_REGIONS.sortedWith(compareBy(collator) { name(it, locale) })
    }
    val system = stringResource(R.string.setting_description_region_status_system)
    ChoiceSetting(
        title = stringResource(R.string.setting_title_region),
        value = region,
        options = regions,
        label = { if (it.isEmpty()) system else name(it, locale) },
        onSelect = { Preferences.YOUTUBE_REGION.update(it) },
    )
}

private fun name(region: String, locale: Locale): String =
    Locale("", region).getDisplayCountry(locale)

/**
 * The settings of while someone is [signedIn], with Original, or of while nobody is. Each keeps
 * its values while the other shows.
 */
@Composable
private fun YouTubeQualitySettings(signedIn: Boolean) =
    if (signedIn)
        StreamQualitySettings(
            wifi = Preferences.YOUTUBE_QUALITY_WIFI_PREMIUM,
            mobile = Preferences.YOUTUBE_QUALITY_MOBILE_PREMIUM,
            label = labelsOf(StreamQuality.entries) { it.youTubeLabelRes },
        )
    else
        StreamQualitySettings(
            wifi = Preferences.YOUTUBE_QUALITY_WIFI,
            mobile = Preferences.YOUTUBE_QUALITY_MOBILE,
            label = labelsOf(StreamQuality.entries) { it.youTubeLabelRes },
            options = StreamQuality.entries - StreamQuality.ORIGINAL,
        )

@ScreenPreviews
@Composable
private fun YouTubeSettingsPreview() = PreviewTheme {
    YouTubeSettingsContent(
        enabled = true,
        account = YouTubeAccountState.SignedIn(YouTubeAccount("Neon Coast", "@neoncoast", null)),
        channels = emptyList(),
        pageId = "",
        region = "VN",
        onEnabledChange = {},
        onSignIn = {},
        onRetry = {},
        onSignOut = {},
        onSwitchChannel = {},
        onBack = {},
        library = {
            LibrarySyncSettings(LibrarySection.entries.associateWith { true }) { _, _ -> }
        },
        quality = {
            StreamQualitySettings(
                wifi = StreamQuality.ORIGINAL,
                mobile = StreamQuality.MEDIUM,
                label = labelsOf(StreamQuality.entries) { it.youTubeLabelRes },
                onWifiChange = {},
                onMobileChange = {},
            )
        },
        proxy = { PreviewProxySettings(stringResource(R.string.source_ytm)) },
    )
}
