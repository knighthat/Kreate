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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The colors of charts whose parts stand for different things (sources, storage parts). They
 * don't follow the theme: the theme's roles come from one seed, and pitch black makes them all
 * white, so its parts would look alike. Each mode has its own steps, checked against
 * color-blindness in this order (neighbors apart) on the app's surfaces.
 */
@Immutable
data class ChartColors(
    val blue: Color,
    val orange: Color,
    val aqua: Color,
    val yellow: Color,
    val pink: Color,
)

private val lightChartColors = ChartColors(
    blue = Color(0xFF2A78D6),
    orange = Color(0xFFEB6834),
    aqua = Color(0xFF1BAF7A),
    yellow = Color(0xFFEDA100),
    pink = Color(0xFFE87BA4),
)

private val darkChartColors = ChartColors(
    blue = Color(0xFF3987E5),
    orange = Color(0xFFD95926),
    aqua = Color(0xFF199E70),
    yellow = Color(0xFFC98500),
    pink = Color(0xFFD55181),
)

internal fun chartColors(darkTheme: Boolean): ChartColors =
    if (darkTheme) darkChartColors else lightChartColors

internal val LocalChartColors = staticCompositionLocalOf { lightChartColors }

/** The [ChartColors] of the current theme's mode. */
val MaterialTheme.chartColors: ChartColors
    @Composable
    @ReadOnlyComposable
    get() = LocalChartColors.current

/** Black or white, whichever reads better on this color, for text drawn over a chart's part. */
fun Color.onChart(): Color =
    // Where white and black on it have the same contrast
    if (luminance() > 0.179f) Color.Black else Color.White
