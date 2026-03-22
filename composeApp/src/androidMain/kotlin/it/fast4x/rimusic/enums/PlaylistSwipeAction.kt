package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class PlaylistSwipeAction(
    @field:DrawableRes override val androidIconId: Int,
    @field:StringRes override val androidTextId: Int,
): Drawable, TextView {

    NoAction( R.drawable.close, R.string.none ),

    PlayNext( R.drawable.play_skip_forward, R.string.play_next ),

    Download( R.drawable.download, R.string.download ),

    Favourite( R.drawable.heart_outline, R.string.favorites ),

    Enqueue( R.drawable.enqueue, R.string.enqueue );
}
