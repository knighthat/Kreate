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

package me.knighthat.kreate.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.knighthat.kreate.ui.navigation.GeneralSettings
import me.knighthat.kreate.ui.navigation.NetworkSettings
import me.knighthat.kreate.ui.navigation.Route
import me.knighthat.kreate.ui.navigation.UiSettings

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
) {
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
            summary = "Connections and your music server",
            onClick = { onOpen(NetworkSettings) },
        )
    }
}
