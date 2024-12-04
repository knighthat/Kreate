package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlayerPosition(
    @field:StringRes override val textId: Int
): TextView {

    Top( R.string.position_top ),
    Bottom( R.string.position_bottom );
}