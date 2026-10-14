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
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle

/**
 * What the user sees and touches: the theme.
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()

    SettingsPage("UI", onBack, modifier) {
        SettingsSection("Theme")
        ChoiceSetting(
            title = "Theme",
            value = themeMode,
            options = ThemeMode.entries,
            label = ThemeMode::prettyName,
            onSelect = Preferences.THEME_MODE::update,
        )
        ChoiceSetting(
            title = "Contrast",
            value = contrastLevel,
            options = ContrastLevel.entries,
            label = ContrastLevel::prettyName,
            onSelect = Preferences.CONTRAST_LEVEL::update,
        )
    }
}
