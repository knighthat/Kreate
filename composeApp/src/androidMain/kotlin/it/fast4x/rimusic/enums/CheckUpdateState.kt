package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class CheckUpdateState(
    @field:StringRes override val textId: Int
): TextView {

    Enabled( R.string.enabled ),
    Disabled( R.string.vt_disabled ),
    Ask( R.string.ask );
}