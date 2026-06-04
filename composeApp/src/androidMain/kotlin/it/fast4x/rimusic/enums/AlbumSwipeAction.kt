package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.add_to_queue
import kreate.resources.generated.resources.blank
import kreate.resources.generated.resources.bookmark
import kreate.resources.generated.resources.bookmark_filled
import kreate.resources.generated.resources.skip_next
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource

enum class AlbumSwipeAction(
    override val iconId: DrawableResource,
    @field:StringRes override val androidTextId: Int,
): Drawable, TextView {

    NoAction(Res.drawable.blank, R.string.none),

    PlayNext(Res.drawable.skip_next, R.string.play_next),

    Bookmark(Res.drawable.bookmark, R.string.bookmark),

    Enqueue(Res.drawable.add_to_queue, R.string.enqueue);

    fun getStateIcon(bookmarkedState: Long?): DrawableResource =
        when ( this ) {
            Bookmark -> when( bookmarkedState ) {
                null -> Res.drawable.bookmark
                else -> Res.drawable.bookmark_filled
            }
            else -> iconId
        }
}
