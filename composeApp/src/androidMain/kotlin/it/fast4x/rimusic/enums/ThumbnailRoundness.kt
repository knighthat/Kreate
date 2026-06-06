package it.fast4x.rimusic.enums

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.roundness_sharp
import kreate.resources.generated.resources.roundness_soft
import kreate.resources.generated.resources.roundness_squircle
import kreate.resources.generated.resources.roundness_subtle
import org.jetbrains.compose.resources.StringResource

enum class ThumbnailRoundness(
    val size: Int,
    override val textId: StringResource
): TextView {

    None(0, Res.string.roundness_sharp),

    Light(8, Res.string.roundness_subtle),

    Medium(12, Res.string.roundness_soft),

    Heavy(16, Res.string.roundness_squircle);

    val shape: Shape
        get() = when( this ) {
            None    -> RectangleShape
            Light   -> RoundedCornerShape(size.dp)
            Medium  -> RoundedCornerShape(size.dp)
            Heavy   -> RoundedCornerShape(size.dp)
        }
}
