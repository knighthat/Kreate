package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class PlayerPosition(
    @field:StringRes override val androidTextId: Int
): TextView {

    Top( R.string.position_top ),
    Bottom( R.string.position_bottom );
}