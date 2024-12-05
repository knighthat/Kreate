package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R

enum class OnDeviceSongSortBy(
    @StringRes val textId: Int,
    @field:DrawableRes override val iconId: Int
): MenuTitle, Drawable {

    Title( R.string.sort_title, R.drawable.text ),

    DateAdded( R.string.sort_date_played, R.drawable.calendar ),

    Artist( R.string.sort_artist, R.drawable.artist ),

    Duration( R.string.sort_duration, R.drawable.time ),

    Album( R.string.sort_album, R.drawable.album );

    override val titleId: Int
        get() = this.textId
}
