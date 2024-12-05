package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R

enum class ArtistSortBy(
    @StringRes val textId: Int,
    @field:DrawableRes override val iconId: Int
): MenuTitle, Drawable {

    Name( R.string.sort_artist, R.drawable.text ),

    DateAdded( R.string.sort_date_added, R.drawable.time );

    override val titleId: Int
        get() = this.textId
}
