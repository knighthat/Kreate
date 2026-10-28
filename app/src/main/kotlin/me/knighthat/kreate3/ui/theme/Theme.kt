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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import com.materialkolor.dynamicColorScheme as seededColorScheme

@Composable
private fun lightScheme() = lightColorScheme(
    primary = colorResource(R.color.primary_light),
    onPrimary = colorResource(R.color.on_primary_light),
    primaryContainer = colorResource(R.color.primary_container_light),
    onPrimaryContainer = colorResource(R.color.on_primary_container_light),
    secondary = colorResource(R.color.secondary_light),
    onSecondary = colorResource(R.color.on_secondary_light),
    secondaryContainer = colorResource(R.color.secondary_container_light),
    onSecondaryContainer = colorResource(R.color.on_secondary_container_light),
    tertiary = colorResource(R.color.tertiary_light),
    onTertiary = colorResource(R.color.on_tertiary_light),
    tertiaryContainer = colorResource(R.color.tertiary_container_light),
    onTertiaryContainer = colorResource(R.color.on_tertiary_container_light),
    error = colorResource(R.color.error_light),
    onError = colorResource(R.color.on_error_light),
    errorContainer = colorResource(R.color.error_container_light),
    onErrorContainer = colorResource(R.color.on_error_container_light),
    background = colorResource(R.color.background_light),
    onBackground = colorResource(R.color.on_background_light),
    surface = colorResource(R.color.surface_light),
    onSurface = colorResource(R.color.on_surface_light),
    surfaceVariant = colorResource(R.color.surface_variant_light),
    onSurfaceVariant = colorResource(R.color.on_surface_variant_light),
    outline = colorResource(R.color.outline_light),
    outlineVariant = colorResource(R.color.outline_variant_light),
    scrim = colorResource(R.color.scrim_light),
    inverseSurface = colorResource(R.color.inverse_surface_light),
    inverseOnSurface = colorResource(R.color.inverse_on_surface_light),
    inversePrimary = colorResource(R.color.inverse_primary_light),
    surfaceDim = colorResource(R.color.surface_dim_light),
    surfaceBright = colorResource(R.color.surface_bright_light),
    surfaceContainerLowest = colorResource(R.color.surface_container_lowest_light),
    surfaceContainerLow = colorResource(R.color.surface_container_low_light),
    surfaceContainer = colorResource(R.color.surface_container_light),
    surfaceContainerHigh = colorResource(R.color.surface_container_high_light),
    surfaceContainerHighest = colorResource(R.color.surface_container_highest_light),
)

@Composable
private fun darkScheme() = darkColorScheme(
    primary = colorResource(R.color.primary_dark),
    onPrimary = colorResource(R.color.on_primary_dark),
    primaryContainer = colorResource(R.color.primary_container_dark),
    onPrimaryContainer = colorResource(R.color.on_primary_container_dark),
    secondary = colorResource(R.color.secondary_dark),
    onSecondary = colorResource(R.color.on_secondary_dark),
    secondaryContainer = colorResource(R.color.secondary_container_dark),
    onSecondaryContainer = colorResource(R.color.on_secondary_container_dark),
    tertiary = colorResource(R.color.tertiary_dark),
    onTertiary = colorResource(R.color.on_tertiary_dark),
    tertiaryContainer = colorResource(R.color.tertiary_container_dark),
    onTertiaryContainer = colorResource(R.color.on_tertiary_container_dark),
    error = colorResource(R.color.error_dark),
    onError = colorResource(R.color.on_error_dark),
    errorContainer = colorResource(R.color.error_container_dark),
    onErrorContainer = colorResource(R.color.on_error_container_dark),
    background = colorResource(R.color.background_dark),
    onBackground = colorResource(R.color.on_background_dark),
    surface = colorResource(R.color.surface_dark),
    onSurface = colorResource(R.color.on_surface_dark),
    surfaceVariant = colorResource(R.color.surface_variant_dark),
    onSurfaceVariant = colorResource(R.color.on_surface_variant_dark),
    outline = colorResource(R.color.outline_dark),
    outlineVariant = colorResource(R.color.outline_variant_dark),
    scrim = colorResource(R.color.scrim_dark),
    inverseSurface = colorResource(R.color.inverse_surface_dark),
    inverseOnSurface = colorResource(R.color.inverse_on_surface_dark),
    inversePrimary = colorResource(R.color.inverse_primary_dark),
    surfaceDim = colorResource(R.color.surface_dim_dark),
    surfaceBright = colorResource(R.color.surface_bright_dark),
    surfaceContainerLowest = colorResource(R.color.surface_container_lowest_dark),
    surfaceContainerLow = colorResource(R.color.surface_container_low_dark),
    surfaceContainer = colorResource(R.color.surface_container_dark),
    surfaceContainerHigh = colorResource(R.color.surface_container_high_dark),
    surfaceContainerHighest = colorResource(R.color.surface_container_highest_dark),
)

@Composable
private fun mediumContrastLightColorScheme() = lightColorScheme(
    primary = colorResource(R.color.primary_light_medium_contrast),
    onPrimary = colorResource(R.color.on_primary_light_medium_contrast),
    primaryContainer = colorResource(R.color.primary_container_light_medium_contrast),
    onPrimaryContainer = colorResource(R.color.on_primary_container_light_medium_contrast),
    secondary = colorResource(R.color.secondary_light_medium_contrast),
    onSecondary = colorResource(R.color.on_secondary_light_medium_contrast),
    secondaryContainer = colorResource(R.color.secondary_container_light_medium_contrast),
    onSecondaryContainer = colorResource(R.color.on_secondary_container_light_medium_contrast),
    tertiary = colorResource(R.color.tertiary_light_medium_contrast),
    onTertiary = colorResource(R.color.on_tertiary_light_medium_contrast),
    tertiaryContainer = colorResource(R.color.tertiary_container_light_medium_contrast),
    onTertiaryContainer = colorResource(R.color.on_tertiary_container_light_medium_contrast),
    error = colorResource(R.color.error_light_medium_contrast),
    onError = colorResource(R.color.on_error_light_medium_contrast),
    errorContainer = colorResource(R.color.error_container_light_medium_contrast),
    onErrorContainer = colorResource(R.color.on_error_container_light_medium_contrast),
    background = colorResource(R.color.background_light_medium_contrast),
    onBackground = colorResource(R.color.on_background_light_medium_contrast),
    surface = colorResource(R.color.surface_light_medium_contrast),
    onSurface = colorResource(R.color.on_surface_light_medium_contrast),
    surfaceVariant = colorResource(R.color.surface_variant_light_medium_contrast),
    onSurfaceVariant = colorResource(R.color.on_surface_variant_light_medium_contrast),
    outline = colorResource(R.color.outline_light_medium_contrast),
    outlineVariant = colorResource(R.color.outline_variant_light_medium_contrast),
    scrim = colorResource(R.color.scrim_light_medium_contrast),
    inverseSurface = colorResource(R.color.inverse_surface_light_medium_contrast),
    inverseOnSurface = colorResource(R.color.inverse_on_surface_light_medium_contrast),
    inversePrimary = colorResource(R.color.inverse_primary_light_medium_contrast),
    surfaceDim = colorResource(R.color.surface_dim_light_medium_contrast),
    surfaceBright = colorResource(R.color.surface_bright_light_medium_contrast),
    surfaceContainerLowest = colorResource(R.color.surface_container_lowest_light_medium_contrast),
    surfaceContainerLow = colorResource(R.color.surface_container_low_light_medium_contrast),
    surfaceContainer = colorResource(R.color.surface_container_light_medium_contrast),
    surfaceContainerHigh = colorResource(R.color.surface_container_high_light_medium_contrast),
    surfaceContainerHighest = colorResource(R.color.surface_container_highest_light_medium_contrast),
)

@Composable
private fun highContrastLightColorScheme() = lightColorScheme(
    primary = colorResource(R.color.primary_light_high_contrast),
    onPrimary = colorResource(R.color.on_primary_light_high_contrast),
    primaryContainer = colorResource(R.color.primary_container_light_high_contrast),
    onPrimaryContainer = colorResource(R.color.on_primary_container_light_high_contrast),
    secondary = colorResource(R.color.secondary_light_high_contrast),
    onSecondary = colorResource(R.color.on_secondary_light_high_contrast),
    secondaryContainer = colorResource(R.color.secondary_container_light_high_contrast),
    onSecondaryContainer = colorResource(R.color.on_secondary_container_light_high_contrast),
    tertiary = colorResource(R.color.tertiary_light_high_contrast),
    onTertiary = colorResource(R.color.on_tertiary_light_high_contrast),
    tertiaryContainer = colorResource(R.color.tertiary_container_light_high_contrast),
    onTertiaryContainer = colorResource(R.color.on_tertiary_container_light_high_contrast),
    error = colorResource(R.color.error_light_high_contrast),
    onError = colorResource(R.color.on_error_light_high_contrast),
    errorContainer = colorResource(R.color.error_container_light_high_contrast),
    onErrorContainer = colorResource(R.color.on_error_container_light_high_contrast),
    background = colorResource(R.color.background_light_high_contrast),
    onBackground = colorResource(R.color.on_background_light_high_contrast),
    surface = colorResource(R.color.surface_light_high_contrast),
    onSurface = colorResource(R.color.on_surface_light_high_contrast),
    surfaceVariant = colorResource(R.color.surface_variant_light_high_contrast),
    onSurfaceVariant = colorResource(R.color.on_surface_variant_light_high_contrast),
    outline = colorResource(R.color.outline_light_high_contrast),
    outlineVariant = colorResource(R.color.outline_variant_light_high_contrast),
    scrim = colorResource(R.color.scrim_light_high_contrast),
    inverseSurface = colorResource(R.color.inverse_surface_light_high_contrast),
    inverseOnSurface = colorResource(R.color.inverse_on_surface_light_high_contrast),
    inversePrimary = colorResource(R.color.inverse_primary_light_high_contrast),
    surfaceDim = colorResource(R.color.surface_dim_light_high_contrast),
    surfaceBright = colorResource(R.color.surface_bright_light_high_contrast),
    surfaceContainerLowest = colorResource(R.color.surface_container_lowest_light_high_contrast),
    surfaceContainerLow = colorResource(R.color.surface_container_low_light_high_contrast),
    surfaceContainer = colorResource(R.color.surface_container_light_high_contrast),
    surfaceContainerHigh = colorResource(R.color.surface_container_high_light_high_contrast),
    surfaceContainerHighest = colorResource(R.color.surface_container_highest_light_high_contrast),
)

@Composable
private fun mediumContrastDarkColorScheme() = darkColorScheme(
    primary = colorResource(R.color.primary_dark_medium_contrast),
    onPrimary = colorResource(R.color.on_primary_dark_medium_contrast),
    primaryContainer = colorResource(R.color.primary_container_dark_medium_contrast),
    onPrimaryContainer = colorResource(R.color.on_primary_container_dark_medium_contrast),
    secondary = colorResource(R.color.secondary_dark_medium_contrast),
    onSecondary = colorResource(R.color.on_secondary_dark_medium_contrast),
    secondaryContainer = colorResource(R.color.secondary_container_dark_medium_contrast),
    onSecondaryContainer = colorResource(R.color.on_secondary_container_dark_medium_contrast),
    tertiary = colorResource(R.color.tertiary_dark_medium_contrast),
    onTertiary = colorResource(R.color.on_tertiary_dark_medium_contrast),
    tertiaryContainer = colorResource(R.color.tertiary_container_dark_medium_contrast),
    onTertiaryContainer = colorResource(R.color.on_tertiary_container_dark_medium_contrast),
    error = colorResource(R.color.error_dark_medium_contrast),
    onError = colorResource(R.color.on_error_dark_medium_contrast),
    errorContainer = colorResource(R.color.error_container_dark_medium_contrast),
    onErrorContainer = colorResource(R.color.on_error_container_dark_medium_contrast),
    background = colorResource(R.color.background_dark_medium_contrast),
    onBackground = colorResource(R.color.on_background_dark_medium_contrast),
    surface = colorResource(R.color.surface_dark_medium_contrast),
    onSurface = colorResource(R.color.on_surface_dark_medium_contrast),
    surfaceVariant = colorResource(R.color.surface_variant_dark_medium_contrast),
    onSurfaceVariant = colorResource(R.color.on_surface_variant_dark_medium_contrast),
    outline = colorResource(R.color.outline_dark_medium_contrast),
    outlineVariant = colorResource(R.color.outline_variant_dark_medium_contrast),
    scrim = colorResource(R.color.scrim_dark_medium_contrast),
    inverseSurface = colorResource(R.color.inverse_surface_dark_medium_contrast),
    inverseOnSurface = colorResource(R.color.inverse_on_surface_dark_medium_contrast),
    inversePrimary = colorResource(R.color.inverse_primary_dark_medium_contrast),
    surfaceDim = colorResource(R.color.surface_dim_dark_medium_contrast),
    surfaceBright = colorResource(R.color.surface_bright_dark_medium_contrast),
    surfaceContainerLowest = colorResource(R.color.surface_container_lowest_dark_medium_contrast),
    surfaceContainerLow = colorResource(R.color.surface_container_low_dark_medium_contrast),
    surfaceContainer = colorResource(R.color.surface_container_dark_medium_contrast),
    surfaceContainerHigh = colorResource(R.color.surface_container_high_dark_medium_contrast),
    surfaceContainerHighest = colorResource(R.color.surface_container_highest_dark_medium_contrast),
)

@Composable
private fun highContrastDarkColorScheme() = darkColorScheme(
    primary = colorResource(R.color.primary_dark_high_contrast),
    onPrimary = colorResource(R.color.on_primary_dark_high_contrast),
    primaryContainer = colorResource(R.color.primary_container_dark_high_contrast),
    onPrimaryContainer = colorResource(R.color.on_primary_container_dark_high_contrast),
    secondary = colorResource(R.color.secondary_dark_high_contrast),
    onSecondary = colorResource(R.color.on_secondary_dark_high_contrast),
    secondaryContainer = colorResource(R.color.secondary_container_dark_high_contrast),
    onSecondaryContainer = colorResource(R.color.on_secondary_container_dark_high_contrast),
    tertiary = colorResource(R.color.tertiary_dark_high_contrast),
    onTertiary = colorResource(R.color.on_tertiary_dark_high_contrast),
    tertiaryContainer = colorResource(R.color.tertiary_container_dark_high_contrast),
    onTertiaryContainer = colorResource(R.color.on_tertiary_container_dark_high_contrast),
    error = colorResource(R.color.error_dark_high_contrast),
    onError = colorResource(R.color.on_error_dark_high_contrast),
    errorContainer = colorResource(R.color.error_container_dark_high_contrast),
    onErrorContainer = colorResource(R.color.on_error_container_dark_high_contrast),
    background = colorResource(R.color.background_dark_high_contrast),
    onBackground = colorResource(R.color.on_background_dark_high_contrast),
    surface = colorResource(R.color.surface_dark_high_contrast),
    onSurface = colorResource(R.color.on_surface_dark_high_contrast),
    surfaceVariant = colorResource(R.color.surface_variant_dark_high_contrast),
    onSurfaceVariant = colorResource(R.color.on_surface_variant_dark_high_contrast),
    outline = colorResource(R.color.outline_dark_high_contrast),
    outlineVariant = colorResource(R.color.outline_variant_dark_high_contrast),
    scrim = colorResource(R.color.scrim_dark_high_contrast),
    inverseSurface = colorResource(R.color.inverse_surface_dark_high_contrast),
    inverseOnSurface = colorResource(R.color.inverse_on_surface_dark_high_contrast),
    inversePrimary = colorResource(R.color.inverse_primary_dark_high_contrast),
    surfaceDim = colorResource(R.color.surface_dim_dark_high_contrast),
    surfaceBright = colorResource(R.color.surface_bright_dark_high_contrast),
    surfaceContainerLowest = colorResource(R.color.surface_container_lowest_dark_high_contrast),
    surfaceContainerLow = colorResource(R.color.surface_container_low_dark_high_contrast),
    surfaceContainer = colorResource(R.color.surface_container_dark_high_contrast),
    surfaceContainerHigh = colorResource(R.color.surface_container_high_dark_high_contrast),
    surfaceContainerHighest = colorResource(R.color.surface_container_highest_dark_high_contrast),
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

@Composable
private fun staticColorScheme(darkTheme: Boolean, contrastLevel: ContrastLevel): ColorScheme =
    if (darkTheme) when (contrastLevel) {
        ContrastLevel.NORMAL -> darkScheme()
        ContrastLevel.MEDIUM -> mediumContrastDarkColorScheme()
        ContrastLevel.HIGH -> highContrastDarkColorScheme()
    } else when (contrastLevel) {
        ContrastLevel.NORMAL -> lightScheme()
        ContrastLevel.MEDIUM -> mediumContrastLightColorScheme()
        ContrastLevel.HIGH -> highContrastLightColorScheme()
    }

/**
 * The scheme with white on black only, for [ThemeMode.PITCH_BLACK]: text and accents are white on
 * a black screen. Containers are a few shades above black, so cards, bars and selected items
 * still stand out, and their text is white too. Secondary text and outlines are neutral greys that
 * get lighter with [contrastLevel]. Errors keep their red, so they aren't missed.
 */
private fun ColorScheme.pitchBlack(contrastLevel: ContrastLevel): ColorScheme {
    val container = Color(0xFF2E2E2E)

    return copy(
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = container,
        onPrimaryContainer = Color.White,
        inversePrimary = Color.Black,
        secondary = Color.White,
        onSecondary = Color.Black,
        secondaryContainer = container,
        onSecondaryContainer = Color.White,
        tertiary = Color.White,
        onTertiary = Color.Black,
        tertiaryContainer = container,
        onTertiaryContainer = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1F1F1F),
        onSurfaceVariant = when (contrastLevel) {
            ContrastLevel.NORMAL -> Color(0xFFBDBDBD)
            ContrastLevel.MEDIUM -> Color(0xFFD6D6D6)
            ContrastLevel.HIGH -> Color(0xFFEEEEEE)
        },
        surfaceTint = Color.Black,
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        outline = when (contrastLevel) {
            ContrastLevel.NORMAL -> Color(0xFF8A8A8A)
            ContrastLevel.MEDIUM -> Color(0xFFA6A6A6)
            ContrastLevel.HIGH -> Color(0xFFCCCCCC)
        },
        outlineVariant = Color(0xFF3A3A3A),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF262626),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF222222),
    )
}

/**
 * @param colorSeed color used as primary by [ThemeMode.THUMBNAIL], [Color.Unspecified] means no seed
 * yet and the static scheme is used. It is runtime-only, never store it.
 */
@Composable
fun KreateTheme(
    themeMode: ThemeMode,
    contrastLevel: ContrastLevel,
    colorSeed: Color,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.AUTOMATIC, ThemeMode.AUTOMATIC_DYNAMIC, ThemeMode.THUMBNAIL -> isSystemInDarkTheme()
        ThemeMode.LIGHT, ThemeMode.LIGHT_DYNAMIC -> false
        ThemeMode.DARK, ThemeMode.DARK_DYNAMIC, ThemeMode.PITCH_BLACK -> true
    }
    val dynamicColor = themeMode == ThemeMode.AUTOMATIC_DYNAMIC
            || themeMode == ThemeMode.LIGHT_DYNAMIC
            || themeMode == ThemeMode.DARK_DYNAMIC

    val colorScheme = when {
        themeMode == ThemeMode.THUMBNAIL && colorSeed.isSpecified -> seededColorScheme(
            seedColor = colorSeed,
            isDark = darkTheme,
            contrastLevel = when (contrastLevel) {
                ContrastLevel.NORMAL -> 0.0
                ContrastLevel.MEDIUM -> 0.5
                ContrastLevel.HIGH -> 1.0
            },
        )

        themeMode == ThemeMode.PITCH_BLACK ->
            staticColorScheme(darkTheme = true, contrastLevel).pitchBlack(contrastLevel)

        // Dynamic color is available on Android 12+, it has no contrast levels
        dynamicColor && contrastLevel == ContrastLevel.NORMAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> staticColorScheme(darkTheme, contrastLevel)
    }

    CompositionLocalProvider(LocalChartColors provides chartColors(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

