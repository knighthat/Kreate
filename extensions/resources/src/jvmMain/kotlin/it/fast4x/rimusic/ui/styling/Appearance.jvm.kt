package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape


@Immutable
actual class Appearance actual constructor(
    actual val colorPalette: ColorPalette,
    actual val typography: Typography,
    actual val thumbnailShape: Shape
) {

    actual operator fun component1(): ColorPalette = colorPalette

    actual operator fun component2(): Typography = typography

    actual operator fun component3(): Shape = thumbnailShape

    actual fun copy(
        colorPalette: ColorPalette,
        typography: Typography,
        thumbnailShape: Shape
    ) = Appearance(colorPalette, typography, thumbnailShape)
}