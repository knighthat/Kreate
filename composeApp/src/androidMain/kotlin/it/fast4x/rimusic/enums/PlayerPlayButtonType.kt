package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlayerPlayButtonType(
    @field:StringRes override val textId: Int
): TextView {

    Disabled( R.string.vt_disabled ),

    Default( R.string._default ),

    Rectangular( R.string.rectangular ),

    CircularRibbed( R.string.circular_ribbed ),

    Square( R.string.square );

    val height: Int
        get() = when (this) {
            Default, Disabled -> 60
            Rectangular -> 70
            CircularRibbed -> 100
            Square -> 80
        }

    val width: Int
        get() = when (this) {
            Default, Disabled -> 60
            Rectangular -> 110
            CircularRibbed -> 100
            Square -> 80

        }
}