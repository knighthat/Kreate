package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class ColorPaletteName(
    @field:StringRes override val androidTextId: Int
): TextView {

    Default( R.string._default ),

    Dynamic( R.string.dynamic ),

    PureBlack( R.string.theme_pure_black ),

    ModernBlack( R.string.theme_modern_black ),

    MaterialYou( R.string.theme_material_you ),

    Customized( R.string.theme_customized ),

    CustomColor( R.string.customcolor);
}
