package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.constant.SortCategory
import me.knighthat.enums.TextView

enum class ArtistSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val androidIconId: Int,
    override val isRandom: Boolean = false
): TextView, Drawable, SortCategory {

    RANDOM( R.string.random, R.drawable.random, true ),

    Name( R.string.sort_artist, R.drawable.text ),

    DateAdded( R.string.sort_date_added, R.drawable.time );
}
