package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlayerTimelineSize(
    val size: Int,
    @field:StringRes override val textId: Int
): TextView {

    Small( 90, R.string.small ),

    Medium( 55, R.string.medium ),

    Big( 30, R.string.big ),

    Biggest( 20, R.string.biggest ),

    Expanded( 0, R.string.expanded );
}