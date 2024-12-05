package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import it.fast4x.rimusic.R

enum class DownloadedStateMedia(
    @field:DrawableRes override val iconId: Int
): Drawable {

    CACHED( R.drawable.download ),

    CACHED_AND_DOWNLOADED( R.drawable.downloaded ),

    DOWNLOADED( R.drawable.downloaded ),

    NOT_CACHED_OR_DOWNLOADED( R.drawable.download );
}