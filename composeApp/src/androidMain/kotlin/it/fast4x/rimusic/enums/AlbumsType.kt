package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class AlbumsType(
    @field:StringRes override val androidTextId: Int
): TextView {

    Favorites( R.string.favorites ),
    Library( R.string.library );
}