package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape


@Immutable
expect class Appearance(
    colorPalette: ColorPalette,
    typography: Typography,
    thumbnailShape: Shape
)

val LocalAppearance = staticCompositionLocalOf<Appearance> { TODO() }
