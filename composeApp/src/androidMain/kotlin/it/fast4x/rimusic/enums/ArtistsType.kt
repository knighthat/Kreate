package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ArtistsType(
    @field:StringRes override val textId: Int
): TextView {

    Favorites( R.string.favorites ),
    Library( R.string.library );
}