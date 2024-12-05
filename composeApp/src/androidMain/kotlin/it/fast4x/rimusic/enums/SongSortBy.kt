package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R

enum class SongSortBy(
    @StringRes val textId: Int,
    @field:DrawableRes override val iconId: Int
): MenuTitle, Drawable {

    PlayTime( R.string.sort_listening_time, R.drawable.trending ),

    Title( R.string.sort_title, R.drawable.text ),

    DateAdded( R.string.sort_date_added, R.drawable.time ),

    DatePlayed( R.string.sort_date_played, R.drawable.calendar ),

    DateLiked( R.string.sort_date_liked, R.drawable.heart ),

    Artist( R.string.sort_artist, R.drawable.artist ),

    Duration( R.string.sort_duration, R.drawable.time ),

    AlbumName( R.string.sort_album, R.drawable.album );

    override val titleId: Int
        get() = this.textId
}
