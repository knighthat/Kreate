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
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import kotlin.math.roundToLong

private const val MIB = 1024L * 1024

/**
 * How the app behaves: scanning the device, keeping songs and images, leaving the app and
 * opening it from the media notification.
 */
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scanInterval by Preferences.LOCAL_SCAN_INTERVAL.collectAsStateWithLifecycle()
    val songCache by Preferences.MEDIA3_CACHE_SIZE.collectAsStateWithLifecycle()
    val songDownloads by Preferences.MEDIA3_DOWNLOAD_SIZE.collectAsStateWithLifecycle()
    val diskCache by Preferences.IMAGE_DISK_CACHE.collectAsStateWithLifecycle()
    val memoryCache by Preferences.IMAGE_MEMORY_CACHE_PERCENT.collectAsStateWithLifecycle()
    val exitDisabled by Preferences.BACK_EXIT_DISABLED.collectAsStateWithLifecycle()
    val exitConfirmation by Preferences.EXIT_CONFIRMATION.collectAsStateWithLifecycle()
    val exitTimeout by Preferences.EXIT_CONFIRMATION_TIMEOUT.collectAsStateWithLifecycle()
    val notificationOpensPlayer by Preferences.NOTIFICATION_OPENS_PLAYER.collectAsStateWithLifecycle()

    GeneralSettingsContent(
        scanInterval = scanInterval,
        songCache = songCache,
        songDownloads = songDownloads,
        diskCache = diskCache,
        memoryCache = memoryCache,
        exitDisabled = exitDisabled,
        exitConfirmation = exitConfirmation,
        exitTimeout = exitTimeout,
        notificationOpensPlayer = notificationOpensPlayer,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param songCache in bytes, like [songDownloads] and [diskCache]
 * @param memoryCache the part of the app's memory, `0.15` for 15%
 */
@Composable
private fun GeneralSettingsContent(
    scanInterval: Int,
    songCache: Long,
    songDownloads: Long,
    diskCache: Long,
    memoryCache: Double,
    exitDisabled: Boolean,
    exitConfirmation: Boolean,
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

        SettingsSection(stringResource(R.string.setting_header_songs))
        NumberSetting(
            title = stringResource(R.string.setting_title_cache),
            value = songCache / MIB,
            min = 128,
            max = 32 * 1024,
            onSave = { Preferences.MEDIA3_CACHE_SIZE.update(it * MIB) },
            note = {
                stringResource(
                    R.string.setting_description_cache,
                    stringResource(R.string.mib, it),
                    stringResource(R.string.source_ytm)
                )
            },
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_downloads),
            value = songDownloads / MIB,
            min = 128,
            max = 256 * 1024,
            onSave = { Preferences.MEDIA3_DOWNLOAD_SIZE.update(it * MIB) },
            note = {
                stringResource(
                    R.string.setting_description_downloads,
                    stringResource(R.string.mib, it),
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_images))
        NumberSetting(
            title = stringResource(R.string.setting_title_disk_cache),
            value = diskCache / MIB,
            min = 16,
            max = 4096,
            onSave = { Preferences.IMAGE_DISK_CACHE.update(it * MIB) },
            note = {
                stringResource(
                    R.string.setting_description_disk_cache,
                    stringResource(R.string.mib, it)
                )
            },
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_memory_cache),
            value = (memoryCache * 100).roundToLong(),
            min = 5,
            max = 50,
            onSave = { Preferences.IMAGE_MEMORY_CACHE_PERCENT.update(it / 100.0) },
            note = {
                stringResource(
                    R.string.setting_description_memory_cache,
                    stringResource(R.string.percent, it)
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_navigation))
        SwitchSetting(
            title = stringResource(R.string.setting_title_dont_exit_with_back),
            checked = exitDisabled,
            onCheckedChange = { Preferences.BACK_EXIT_DISABLED.update(it) },
            summary = stringResource(R.string.setting_description_dont_exit_with_back),
        )
        if (!exitDisabled) {
            SwitchSetting(
                title = stringResource(R.string.setting_title_confirm_exit),
                checked = exitConfirmation,
                onCheckedChange = { Preferences.EXIT_CONFIRMATION.update(it) },
                summary = stringResource(R.string.setting_description_confirm_exit),
            )
            if (exitConfirmation)
                NumberSetting(
                    title = stringResource(R.string.setting_title_confirmation_time),
                    value = exitTimeout.toLong(),
                    min = 1,
                    max = 10,
                    onSave = { Preferences.EXIT_CONFIRMATION_TIMEOUT.update(it.toInt()) },
                    note = {
                        stringResource(
                            R.string.setting_description_confirmation_time,
                            pluralStringResource(R.plurals.second, it.toInt(), it)
                        )
                    },
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
        songCache = 1024 * MIB,
        songDownloads = 4096 * MIB,
        diskCache = 128 * MIB,
        memoryCache = 0.15,
        exitDisabled = false,
        exitConfirmation = true,
        exitTimeout = 2,
        notificationOpensPlayer = true,
        onBack = {},
    )
}
