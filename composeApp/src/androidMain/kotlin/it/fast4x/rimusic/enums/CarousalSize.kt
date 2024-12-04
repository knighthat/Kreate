package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class CarouselSize(
    @field:StringRes override val textId: Int
): TextView {

    Small( R.string.small ),

    Medium( R.string.medium ),

    Big( R.string.big ),

    Biggest( R.string.biggest ),

    Expanded( R.string.expanded );

    val size: Int
        get() = when (this) {
            Small -> 90
            Medium -> 55
            Big -> 30
            Biggest -> 20
            Expanded -> 0
        }

}
