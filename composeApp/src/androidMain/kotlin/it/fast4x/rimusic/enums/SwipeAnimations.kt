package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class SwipeAnimationNoThumbnail(
    @field:StringRes override val androidTextId: Int
): TextView {

    Sliding( R.string.te_slide_vertical ),

    Fade( R.string.te_fade ),

    Scale( R.string.te_scale ),

    Carousel( R.string.carousel ),

    Circle( R.string.vt_circular )
}