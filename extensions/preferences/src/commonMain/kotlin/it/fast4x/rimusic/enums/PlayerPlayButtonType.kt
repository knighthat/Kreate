package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.shape_rectangular
import kreate.resources.generated.resources.shape_square
import kreate.resources.generated.resources.shape_wavy_circle
import kreate.resources.generated.resources.word_default
import kreate.resources.generated.resources.word_disabled
import org.jetbrains.compose.resources.StringResource

enum class PlayerPlayButtonType(
    val height: Int,
    val width: Int,
    override val textId: StringResource
): TextView {

    Disabled(60, 60, Res.string.word_disabled),

    Default(60, 60, Res.string.word_default),

    Rectangular(70, 110, Res.string.shape_rectangular),

    CircularRibbed(100, 100, Res.string.shape_wavy_circle),

    Square(80, 80, Res.string.shape_square);
}