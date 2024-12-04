package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ColorPaletteName(
    @field:StringRes override val textId: Int
): TextView {

    Default( R.string._default ),

    Dynamic( R.string.dynamic ),

    PureBlack( R.string.theme_pure_black ),

    ModernBlack( R.string.theme_modern_black ),

    MaterialYou( R.string.theme_material_you ),

    Customized( R.string.theme_customized ),

    CustomColor( R.string.customcolor);
}
