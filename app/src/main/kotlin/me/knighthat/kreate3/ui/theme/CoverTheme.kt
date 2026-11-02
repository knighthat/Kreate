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

package me.knighthat.kreate3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.isSpecified
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState
import com.materialkolor.dynamicColorScheme as seededColorScheme

/**
 * The colors of [content] seeded by its own cover, [models], like an album's or an artist's page.
 * Only while the theme is [ThemeMode.THUMBNAIL] and nothing is in the player, whose song seeds
 * the whole app otherwise; the app's colors stay meanwhile and until the cover's color is known.
 * The seed lives only as long as the screen.
 */
@Composable
fun CoverTheme(models: List<Any>, content: @Composable () -> Unit) {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()
    val playing by koinInject<PlaybackController>().state.collectFlowAsState()
    val enabled = themeMode == ThemeMode.THUMBNAIL && playing.current == null
    val seed = rememberCoverSeed(if (enabled) models else emptyList())
    val isDark = isSystemInDarkTheme()
    val app = MaterialTheme.colorScheme

    // Always wrapped, so the screen keeps its state when the colors change
    val colorScheme = remember(enabled, seed, isDark, contrastLevel, app) {
        if (enabled && seed.isSpecified)
            seededColorScheme(seed, isDark, contrastLevel = contrastLevel.seedContrast)
        else
            app
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
