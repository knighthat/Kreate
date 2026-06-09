package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape


@Immutable
expect class Appearance(
    colorPalette: ColorPalette,
    typography: Typography,
    thumbnailShape: Shape
) {

    val colorPalette: ColorPalette
    val typography: Typography
    val thumbnailShape: Shape

    operator fun component1(): ColorPalette

    operator fun component2(): Typography

    operator fun component3(): Shape

    fun copy(
        colorPalette: ColorPalette = this.colorPalette,
        typography: Typography = this.typography,
        thumbnailShape: Shape = this.thumbnailShape
    ): Appearance
}

val LocalAppearance = staticCompositionLocalOf<Appearance> { TODO() }
