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
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle

private const val MIB = 1024L * 1024

private const val NEXT_LAUNCH = "Applies from the next launch"

/**
 * How the app behaves: scanning the device, keeping songs and images, and leaving the app.
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

    SettingsPage("General", onBack, modifier) {
        SettingsSection("Library")
        NumberSetting(
            title = "Scan interval",
            value = scanInterval.toLong(),
            unit = "minutes",
            min = 15,
            max = 24 * 60,
            onSave = { Preferences.LOCAL_SCAN_INTERVAL.update(it.toInt()) },
            note = "How often the device is searched for new music",
        )

        SettingsSection("Songs")
        NumberSetting(
            title = "Cache",
            value = songCache / MIB,
            unit = "MiB",
            min = 128,
            max = 32 * 1024,
            onSave = { Preferences.MEDIA3_CACHE_SIZE.update(it * MIB) },
            note = "Songs streamed from a server or YouTube. $NEXT_LAUNCH",
        )
        NumberSetting(
            title = "Downloads",
            value = songDownloads / MIB,
            unit = "MiB",
            min = 128,
            max = 256 * 1024,
            onSave = { Preferences.MEDIA3_DOWNLOAD_SIZE.update(it * MIB) },
            note = "Songs kept to play offline, separate from the cache",
        )

        SettingsSection("Images")
        NumberSetting(
            title = "Disk cache",
            value = diskCache / MIB,
            unit = "MiB",
            min = 16,
            max = 4096,
            onSave = { Preferences.IMAGE_DISK_CACHE.update(it * MIB) },
            note = NEXT_LAUNCH,
        )
        NumberSetting(
            title = "Memory cache",
            value = Math.round(memoryCache * 100),
            unit = "% of app memory",
            min = 5,
            max = 50,
            onSave = { Preferences.IMAGE_MEMORY_CACHE_PERCENT.update(it / 100.0) },
            note = NEXT_LAUNCH,
        )

        SettingsSection("Navigation")
        SwitchSetting(
            title = "Don't exit with back",
            checked = exitDisabled,
            onCheckedChange = { Preferences.BACK_EXIT_DISABLED.update(it) },
            summary = "Going back from the first screen does nothing",
        )
        if (!exitDisabled) {
            SwitchSetting(
                title = "Confirm exit",
                checked = exitConfirmation,
                onCheckedChange = { Preferences.EXIT_CONFIRMATION.update(it) },
                summary = "Go back twice from the first screen to exit",
            )
            if (exitConfirmation)
                NumberSetting(
                    title = "Confirmation time",
                    value = exitTimeout.toLong(),
                    unit = "seconds",
                    min = 1,
                    max = 10,
                    onSave = { Preferences.EXIT_CONFIRMATION_TIMEOUT.update(it.toInt()) },
                    note = "How long the second time is waited for",
                )
        }
    }
}
