package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class BuiltInPlaylist(
    @field:DrawableRes override val iconId: Int,
    @field:StringRes override val textId: Int
): Drawable, TextView {

    All( R.drawable.musical_notes, R.string.songs ),

    Favorites( R.drawable.heart, R.string.favorites ),

    Offline( R.drawable.sync, R.string.cached ),

    Downloaded( R.drawable.downloaded, R.string.downloaded ),

    Top( R.drawable.trending, R.string.playlist_top ),

    OnDevice( R.drawable.musical_notes, R.string.on_device )
}
