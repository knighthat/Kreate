package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class PlayerControlsType(
    @field:StringRes override val androidTextId: Int
): TextView {

    Essential( R.string.pcontrols_essential ),
    Modern( R.string.pcontrols_modern );
}