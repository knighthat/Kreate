package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.menu_style_grid
import kreate.resources.generated.resources.menu_style_list
import org.jetbrains.compose.resources.StringResource

enum class MenuStyle(
    override val textId: StringResource
): TextView {

    List(Res.string.menu_style_list),
    Grid(Res.string.menu_style_grid);
}
