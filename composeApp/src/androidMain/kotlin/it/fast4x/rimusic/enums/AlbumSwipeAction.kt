package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.action_add_to_queue
import kreate.resources.generated.resources.action_bookmark
import kreate.resources.generated.resources.action_none
import kreate.resources.generated.resources.action_play_next
import kreate.resources.generated.resources.add_to_queue
import kreate.resources.generated.resources.blank
import kreate.resources.generated.resources.bookmark
import kreate.resources.generated.resources.bookmark_filled
import kreate.resources.generated.resources.skip_next
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class AlbumSwipeAction(
    override val iconId: DrawableResource,
    override val textId: StringResource
): Drawable, TextView {

    NoAction(Res.drawable.blank, Res.string.action_none),

    PlayNext(Res.drawable.skip_next, Res.string.action_play_next),

    Bookmark(Res.drawable.bookmark, Res.string.action_bookmark),

    Enqueue(Res.drawable.add_to_queue, Res.string.action_add_to_queue);

    fun getStateIcon(bookmarkedState: Long?): DrawableResource =
        when ( this ) {
            Bookmark -> when( bookmarkedState ) {
                null -> Res.drawable.bookmark
                else -> Res.drawable.bookmark_filled
            }
            else -> iconId
        }
}
