package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlaylistSongSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    Album( R.string.sort_album, R.drawable.album ),

    AlbumYear( R.string.sort_album_year, R.drawable.calendar ),

    Artist( R.string.sort_artist, R.drawable.artist ),

    ArtistAndAlbum( -1, R.drawable.artist ),

    DatePlayed( R.string.sort_date_played, R.drawable.up_right_arrow ),

    PlayTime( R.string.sort_listening_time, R.drawable.trending ),

    Position( R.string.sort_position, R.drawable.position ),

    Title( R.string.sort_title, R.drawable.text ),

    Duration( R.string.sort_duration, R.drawable.time ),

    DateLiked( R.string.sort_date_liked, R.drawable.heart ),

    DateAdded( R.string.sort_date_added, R.drawable.time );

    override val text: String
        @Composable
        get() = when( this ) {
            ArtistAndAlbum -> "${Artist.text}, ${Album.text}"
            else -> super.text
        }
}
