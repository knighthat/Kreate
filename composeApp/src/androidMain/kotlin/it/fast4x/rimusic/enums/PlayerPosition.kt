package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.position_bottom
import kreate.resources.generated.resources.position_top
import org.jetbrains.compose.resources.StringResource

enum class PlayerPosition(
    override val textId: StringResource
): TextView {

    Top(Res.string.position_top),
    Bottom(Res.string.position_bottom);
}