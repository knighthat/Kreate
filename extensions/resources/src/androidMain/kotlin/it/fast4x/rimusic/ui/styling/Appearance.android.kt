package it.fast4x.rimusic.ui.styling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp


@Immutable
actual class Appearance actual constructor(
    actual val colorPalette: ColorPalette,
    actual val typography: Typography,
    actual val thumbnailShape: Shape
) {
    companion object : Saver<Appearance, List<Any>> {
        @Suppress("UNCHECKED_CAST")
        override fun restore(value: List<Any>): Appearance {
            return Appearance(
                colorPalette = ColorPalette.restore(value[0] as List<Any>),
                typography = Typography.restore(value[1] as List<Any>),
                thumbnailShape = RoundedCornerShape((value[2] as Int).dp)
            )
        }

        override fun SaverScope.save(value: Appearance): List<Any> {
            return listOf(
                with(ColorPalette.Companion) { save(value.colorPalette) },
                with(Typography.Companion) { save(value.typography) },
                when (value.thumbnailShape) {
                    RoundedCornerShape(8.dp) -> 8
                    RoundedCornerShape(12.dp) -> 12
                    RoundedCornerShape(16.dp) -> 16
                    else -> 0
                }

            )
        }
    }

    actual operator fun component1(): ColorPalette = colorPalette

    actual operator fun component2(): Typography = typography

    actual operator fun component3(): Shape = thumbnailShape

    actual fun copy(
        colorPalette: ColorPalette,
        typography: Typography,
        thumbnailShape: Shape
    ) = Appearance(colorPalette, typography, thumbnailShape)
}
