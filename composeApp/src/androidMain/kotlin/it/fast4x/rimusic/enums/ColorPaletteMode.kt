package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class ColorPaletteMode(
    @field:StringRes override val androidTextId: Int
): TextView {

    Light( R.string.theme_light ),

    Dark( R.string.theme_dark ),

    PitchBlack( R.string.theme_pitch_black ),

    System( R.string.theme_system );
}
