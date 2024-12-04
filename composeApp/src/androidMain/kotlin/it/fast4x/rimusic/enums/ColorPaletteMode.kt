package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ColorPaletteMode(
    @field:StringRes override val textId: Int
): TextView {

    Light( R.string._light ),

    Dark( R.string.dark ),

    PitchBlack( R.string.system ),

    System( R.string.theme_mode_pitch_black );
}
