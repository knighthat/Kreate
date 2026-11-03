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

package me.knighthat.kreate3.ui.screen.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.settings.NumberSetting
import me.knighthat.kreate3.ui.screen.settings.SecondSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting

/**
 * How the app behaves: scanning the device, leaving the app and opening it from the media
 * notification. What is kept on the device is on the Data page.
 */
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scanInterval by Preferences.LOCAL_SCAN_INTERVAL.collectAsStateWithLifecycle()
    val exitOnBack by Preferences.EXIT_ON_BACK.collectAsStateWithLifecycle()
    val exitTimeout by Preferences.EXIT_CONFIRMATION_TIMEOUT.collectAsStateWithLifecycle()
    val notificationOpensPlayer by Preferences.NOTIFICATION_OPENS_PLAYER.collectAsStateWithLifecycle()

    GeneralSettingsContent(
        scanInterval = scanInterval,
        exitOnBack = exitOnBack,
        exitTimeout = exitTimeout,
        notificationOpensPlayer = notificationOpensPlayer,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun GeneralSettingsContent(
    scanInterval: Int,
    exitOnBack: Boolean,
    exitTimeout: Int,
    notificationOpensPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(stringResource(R.string.screen_title_general), onBack, modifier) {
        SettingsSection(stringResource(R.string.setting_header_library))
        NumberSetting(
            title = stringResource(R.string.setting_title_scan_interval),
            value = scanInterval.toLong(),
            min = 15,
            max = 24 * 60,
            onSave = { Preferences.LOCAL_SCAN_INTERVAL.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_scan_interval,
                    pluralStringResource(R.plurals.minute, it.toInt(), it)
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_navigation))
        SwitchSetting(
            title = stringResource(R.string.setting_title_exit_on_back),
            checked = exitOnBack,
            onCheckedChange = { Preferences.EXIT_ON_BACK.update(it) },
            summary = stringResource(
                if (exitOnBack)
                    R.string.setting_description_exit_on_back_on
                else
                    R.string.setting_description_exit_on_back_off
            ),
        )
        if (exitOnBack) {
            val off = stringResource(R.string.status_off)
            // 0 seconds closes the app at once
            SecondSetting(
                title = stringResource(R.string.setting_title_confirm_exit),
                value = exitTimeout,
                range = 0..10,
                onSelect = { Preferences.EXIT_CONFIRMATION_TIMEOUT.update(it) },
                summary = if (exitTimeout == 0) {
                    stringResource(R.string.setting_description_confirm_exit_status_off)
                } else {
                    stringResource(
                        R.string.setting_description_confirm_exit_status_on,
                        pluralStringResource(R.plurals.second, exitTimeout, exitTimeout)
                    )
                },
                number = { if (it == 0) off else it.toString() },
            )
        }
        SwitchSetting(
            title = stringResource(R.string.setting_title_notification_opens_player),
            checked = notificationOpensPlayer,
            onCheckedChange = { Preferences.NOTIFICATION_OPENS_PLAYER.update(it) },
            summary = stringResource(R.string.setting_description_notification_opens_player),
        )
    }
}

@ScreenPreviews
@Composable
private fun GeneralSettingsPreview() = PreviewTheme {
    GeneralSettingsContent(
        scanInterval = 30,
        exitOnBack = true,
        exitTimeout = 2,
        notificationOpensPlayer = true,
        onBack = {},
    )
}
