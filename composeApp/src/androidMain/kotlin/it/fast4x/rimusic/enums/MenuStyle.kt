package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class MenuStyle(
    @field:StringRes override val textId: Int
): TextView {

    List( R.string.style_list ),
    Grid( R.string.style_grid );
}
