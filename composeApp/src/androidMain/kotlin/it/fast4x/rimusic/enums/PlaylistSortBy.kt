package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlaylistSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    MostPlayed( R.string.sort_listening_time, R.drawable.trending ),

    Name( R.string.sort_name, R.drawable.text ),

    DateAdded( R.string.sort_date_added, R.drawable.calendar ),

    SongCount( R.string.sort_songs_number, R.drawable.medical );
}
