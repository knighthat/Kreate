package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ThumbnailType(
    @field:StringRes override val textId: Int
): TextView {

    Essential( R.string.pcontrols_essential ),
    Modern( R.string.pcontrols_modern );
}