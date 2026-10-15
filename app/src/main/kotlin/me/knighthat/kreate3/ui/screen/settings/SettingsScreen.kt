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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.ui.navigation.BackupSettings
import me.knighthat.kreate3.ui.navigation.GeneralSettings
import me.knighthat.kreate3.ui.navigation.NetworkSettings
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.UiSettings
import org.koin.compose.koinInject

/**
 * The settings pages to pick from.
 *
 * @param onOpen called with the route of the page the user picked
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    profileManager: ProfileManager = koinInject(),
) {
    val activeId by profileManager.activeId.collectAsStateWithLifecycle()

    SettingsPage("Settings", onBack, modifier) {
        SettingsItem(
            title = "General",
            summary = "How the app behaves",
            onClick = { onOpen(GeneralSettings) },
        )
        SettingsItem(
            title = "UI",
            summary = "Theme and what you interact with",
            onClick = { onOpen(UiSettings) },
        )
        SettingsItem(
            title = "Network",
            summary = "Connections, your music server and YouTube account",
            onClick = { onOpen(NetworkSettings) },
        )
        // A backup holds every profile, so only the default one may save or replace them
        if (activeId == Profile.DEFAULT_ID)
            SettingsItem(
                title = "Backup",
                summary = "Save or restore your profiles, settings and library",
                onClick = { onOpen(BackupSettings) },
            )
    }
}
