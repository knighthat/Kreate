package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import it.fast4x.rimusic.R

enum class BuiltInPlaylist(
    @field:DrawableRes override val iconId: Int
): Drawable {

    All( R.drawable.musical_notes ),

    Favorites( R.drawable.heart ),

    Offline( R.drawable.sync ),

    Downloaded( R.drawable.downloaded ),

    Top( R.drawable.trending ),

    OnDevice( R.drawable.musical_notes );
}
