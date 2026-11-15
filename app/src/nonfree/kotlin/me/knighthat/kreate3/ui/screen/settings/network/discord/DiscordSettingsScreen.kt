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

package me.knighthat.kreate3.ui.screen.settings.network.discord

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.navigation.DiscordSettings
import me.knighthat.kreate3.discord.DiscordUser
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.presence.DiscordAccount
import me.knighthat.kreate3.presence.DiscordAccountState
import me.knighthat.kreate3.presence.DiscordCovers
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.fileSize
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.settings.MainSwitch
import me.knighthat.kreate3.ui.screen.settings.PixelSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SliderSetting
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** Who is signed in to Discord, for the row that opens its page. */
@Composable
private fun DiscordAccountState.summary(): String = when (this) {
    DiscordAccountState.SignedOut -> stringResource(R.string.setting_description_status_not_signed_in)
    DiscordAccountState.SigningIn -> stringResource(R.string.setting_description_sign_in_status_waiting)
    DiscordAccountState.SignedIn -> stringResource(R.string.status_signed_in)
    is DiscordAccountState.Known -> stringResource(
        R.string.setting_description_sign_in_status_signed_in_as,
        user.displayName ?: user.username
    )
}

/** What the row of Network settings that opens Discord's page says: off, or who is signed in. */
@Composable
fun discordSummary(account: DiscordAccount = koinInject()): String {
    val enabled by Preferences.DISCORD_ENABLED.collectAsStateWithLifecycle()
    val state by account.state.collectFlowAsState()
    return if (enabled) state.summary() else stringResource(R.string.status_off)
}

/** Discord's page, see [DiscordSettingsScreen]. */
fun EntryProviderScope<NavKey>.discordSettingsEntry(onBack: () -> Unit) {
    entry<DiscordSettings> { DiscordSettingsScreen(onBack) }
}

/**
 * Discord's Rich Presence: the switch, the account of the active profile, which sources show and
 * the covers uploaded for it.
 */
@Composable
fun DiscordSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    account: DiscordAccount = koinInject(),
) {
    val enabled by Preferences.DISCORD_ENABLED.collectAsStateWithLifecycle()
    val state by account.state.collectFlowAsState()
    val youTube by Preferences.DISCORD_YOUTUBE.collectAsStateWithLifecycle()
    val openSubsonic by Preferences.DISCORD_OPEN_SUBSONIC.collectAsStateWithLifecycle()
    val local by Preferences.DISCORD_LOCAL.collectAsStateWithLifecycle()
    val coverSize by Preferences.DISCORD_COVER_SIZE.collectAsStateWithLifecycle()
    val wifiBytes by Preferences.DISCORD_COVER_BYTES_WIFI.collectAsStateWithLifecycle()
    val mobileBytes by Preferences.DISCORD_COVER_BYTES_MOBILE.collectAsStateWithLifecycle()
    val binary by Preferences.BINARY_SIZES.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()

    // Who it is, once per profile
    LaunchedEffect(state) {
        if (state == DiscordAccountState.SignedIn) account.refreshUser()
    }
    // Back from Discord's page without an answer, the user left it
    LifecycleResumeEffect(account) {
        account.abandon()
        onPauseOrDispose {}
    }

    DiscordSettingsContent(
        enabled = enabled,
        state = state,
        youTube = youTube,
        openSubsonic = openSubsonic,
        local = local,
        coverSize = coverSize,
        wifiBytes = wifiBytes,
        mobileBytes = mobileBytes,
        binary = binary,
        onSignIn = { openLink(account.start()) },
        onSignOut = account::signOut,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param wifiBytes largest cover uploaded on Wi-Fi, see [Preferences.DISCORD_COVER_BYTES_WIFI]
 * @param binary whether sizes are in units of 1024, see [Preferences.BINARY_SIZES]
 */
@Composable
private fun DiscordSettingsContent(
    enabled: Boolean,
    state: DiscordAccountState,
    youTube: Boolean,
    openSubsonic: Boolean,
    local: Boolean,
    coverSize: Int,
    wifiBytes: Int,
    mobileBytes: Int,
    binary: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val discord = stringResource(R.string.discord)
    val resources = LocalResources.current
    var askSignOut by remember { mutableStateOf(false) }

    SettingsPage(discord, onBack, modifier) {
        MainSwitch(
            title = stringResource(R.string.setting_title_discord_enabled, discord),
            summary = stringResource(
                if (enabled)
                    R.string.setting_description_discord_enabled_on
                else
                    R.string.setting_description_discord_enabled_off,
                discord
            ),
            checked = enabled,
            onCheckedChange = { Preferences.DISCORD_ENABLED.update(it) },
        )
        // The account and the values stay when hidden
        if (!enabled) return@SettingsPage

        SettingsSection(stringResource(R.string.setting_title_account))
        when (state) {
            DiscordAccountState.SignedOut, DiscordAccountState.SigningIn -> SettingsItem(
                title = stringResource(R.string.setting_title_sign_in),
                summary = if (state == DiscordAccountState.SigningIn)
                    stringResource(R.string.setting_description_sign_in_status_waiting)
                else
                    stringResource(R.string.setting_description_sign_in_discord, discord),
                // Again while waiting, in case the page was closed
                onClick = onSignIn,
                trailing = {
                    Icon(painterResource(R.drawable.chevron_right), contentDescription = null)
                },
            )

            DiscordAccountState.SignedIn, is DiscordAccountState.Known -> SettingsItem(
                title = (state as? DiscordAccountState.Known)?.user?.let {
                    it.displayName ?: it.username
                }
                    ?: stringResource(R.string.status_signed_in),
                summary = stringResource(R.string.setting_description_tap_to_sign_out),
                onClick = { askSignOut = true },
            )
        }

        SettingsSection(stringResource(R.string.setting_header_sources))
        val sourceSummary = stringResource(R.string.setting_description_discord_source)
        SwitchSetting(
            title = stringResource(R.string.source_ytm),
            checked = youTube,
            onCheckedChange = { Preferences.DISCORD_YOUTUBE.update(it) },
            summary = sourceSummary,
        )
        SwitchSetting(
            title = stringResource(R.string.source_opensubsonic),
            checked = openSubsonic,
            onCheckedChange = { Preferences.DISCORD_OPEN_SUBSONIC.update(it) },
            summary = stringResource(R.string.setting_description_discord_source_uploaded),
        )
        SwitchSetting(
            title = stringResource(R.string.source_local),
            checked = local,
            onCheckedChange = { Preferences.DISCORD_LOCAL.update(it) },
            summary = stringResource(R.string.setting_description_discord_source_uploaded),
        )

        SettingsSection(stringResource(R.string.setting_header_discord_covers))
        PixelSetting(
            title = stringResource(R.string.setting_title_discord_cover_resolution),
            value = coverSize,
            range = DiscordCovers.resolutions.first..DiscordCovers.resolutions.last,
            step = DiscordCovers.resolutions.step,
            fillFrom = DiscordCovers.ADVISED_RESOLUTION,
            summary = stringResource(
                R.string.setting_description_discord_covers,
                stringResource(R.string.litterbox)
            ),
            onSelect = { Preferences.DISCORD_COVER_SIZE.update(it) },
        )
        val sizes = DiscordCovers.sizes(binary)
        SliderSetting(
            title = stringResource(R.string.setting_title_on_wifi),
            value = DiscordCovers.snap(wifiBytes, binary),
            options = sizes,
            label = { resources.fileSize(it.toLong(), binary) },
            onSelect = { Preferences.DISCORD_COVER_BYTES_WIFI.update(it) },
        )
        SliderSetting(
            title = stringResource(R.string.setting_title_on_mobile_data),
            value = DiscordCovers.snap(mobileBytes, binary),
            options = sizes,
            label = { resources.fileSize(it.toLong(), binary) },
            onSelect = { Preferences.DISCORD_COVER_BYTES_MOBILE.update(it) },
        )

        if (askSignOut)
            AlertDialog(
                onDismissRequest = { askSignOut = false },
                title = { Text(stringResource(R.string.dialog_title_sign_out, discord)) },
                text = { Text(stringResource(R.string.dialog_body_sign_out_of_discord, discord)) },
                confirmButton = {
                    TextButton(onClick = {
                        askSignOut = false
                        onSignOut()
                    }) { Text(stringResource(R.string.action_sign_out)) }
                },
                dismissButton = {
                    TextButton(onClick = { askSignOut = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
    }
}

@ScreenPreviews
@Composable
private fun DiscordSettingsPreview() = PreviewTheme {
    DiscordSettingsContent(
        enabled = true,
        state = DiscordAccountState.Known(DiscordUser("1", "neoncoast", "Neon Coast", null)),
        youTube = true,
        openSubsonic = true,
        local = false,
        coverSize = 1024,
        wifiBytes = 524_288,
        mobileBytes = 262_144,
        binary = true,
        onSignIn = {},
        onSignOut = {},
        onBack = {},
    )
}
