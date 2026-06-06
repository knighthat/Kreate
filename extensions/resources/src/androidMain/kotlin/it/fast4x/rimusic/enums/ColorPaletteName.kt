package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.palette_name_custom_color
import kreate.resources.generated.resources.palette_name_customized
import kreate.resources.generated.resources.palette_name_default
import kreate.resources.generated.resources.palette_name_dynamic
import kreate.resources.generated.resources.palette_name_material_you
import kreate.resources.generated.resources.palette_name_modern_black
import kreate.resources.generated.resources.palette_name_pure_black
import org.jetbrains.compose.resources.StringResource

enum class ColorPaletteName(
    override val textId: StringResource
): TextView {

    Default(Res.string.palette_name_default),

    Dynamic(Res.string.palette_name_dynamic),

    PureBlack(Res.string.palette_name_pure_black),

    ModernBlack(Res.string.palette_name_modern_black),

    MaterialYou(Res.string.palette_name_material_you),

    Customized(Res.string.palette_name_customized),

    CustomColor(Res.string.palette_name_custom_color);
}
