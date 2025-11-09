package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.constant.SortCategory
import me.knighthat.enums.TextView

enum class AlbumSortBy(
    @field:StringRes override val androidTextId: Int,
    @field:DrawableRes override val androidIconId: Int,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM( R.string.random, R.drawable.random, true ),

    Title( R.string.sort_album, R.drawable.text ),

    Year( R.string.sort_album_year, R.drawable.calendar ),

    DateAdded( R.string.sort_date_added, R.drawable.time ),

    Artist( R.string.sort_artist, R.drawable.artist ),

    Songs( R.string.sort_songs_number, R.drawable.medical ),

    Duration( R.string.sort_duration, R.drawable.time );
}
