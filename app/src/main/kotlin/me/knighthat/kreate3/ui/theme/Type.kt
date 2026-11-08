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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import me.knighthat.kreate3.R

/** The weights a variable font is asked for. */
internal val weights = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

@OptIn(ExperimentalTextApi::class)
val interFontFamily = FontFamily(
    weights.map { weight ->
        Font(
            resId = R.font.inter,
            weight = weight,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
)

/** The name of [interFontFamily], shown where the font is named. */
const val INTER_NAME = "Inter"

// Default Material 3 typography values
val baseline = Typography()

/** Material 3's text styles, all set in [family]. */
fun appTypography(family: FontFamily) = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = family),
    displayMedium = baseline.displayMedium.copy(fontFamily = family),
    displaySmall = baseline.displaySmall.copy(fontFamily = family),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = family),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = family),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = family),
    titleLarge = baseline.titleLarge.copy(fontFamily = family),
    titleMedium = baseline.titleMedium.copy(fontFamily = family),
    titleSmall = baseline.titleSmall.copy(fontFamily = family),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = family),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = family),
    bodySmall = baseline.bodySmall.copy(fontFamily = family),
    labelLarge = baseline.labelLarge.copy(fontFamily = family),
    labelMedium = baseline.labelMedium.copy(fontFamily = family),
    labelSmall = baseline.labelSmall.copy(fontFamily = family),
)
