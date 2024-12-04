package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PipModule(
    @field:StringRes override val textId: Int
): TextView {

    Cover( R.string.pipmodule_cover )
}