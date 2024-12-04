package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class OnDeviceFolderSortBy(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    Title( R.string.sort_title, R.drawable.text ),

    Artist( R.string.sort_artist, R.drawable.artist ),

    Duration( R.string.sort_duration, R.drawable.time );
}
