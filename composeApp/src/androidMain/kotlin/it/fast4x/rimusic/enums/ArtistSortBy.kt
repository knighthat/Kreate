package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ArtistSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    Name( R.string.sort_artist, R.drawable.text ),

    DateAdded( R.string.sort_date_added, R.drawable.time );
}
