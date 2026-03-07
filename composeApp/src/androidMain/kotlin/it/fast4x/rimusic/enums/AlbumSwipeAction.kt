package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class AlbumSwipeAction(
    @field:DrawableRes override val androidIconId: Int,
    @field:StringRes override val androidTextId: Int,
): Drawable, TextView {

    NoAction( R.drawable.close, R.string.none ),

    PlayNext( R.drawable.play_skip_forward, R.string.play_next ),

    Bookmark( R.drawable.bookmark_outline, R.string.bookmark ),

    Enqueue( R.drawable.enqueue, R.string.enqueue );

    fun getStateIcon(bookmarkedState: Long?): Int? {
        return when (this) {
            Bookmark -> when(bookmarkedState) {
                null -> R.drawable.bookmark_outline
                else -> R.drawable.bookmark
            }
            NoAction -> null
            else -> androidIconId
        }
    }
}
