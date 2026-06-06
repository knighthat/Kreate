package it.fast4x.rimusic.enums

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.action_add_to_queue
import kreate.resources.generated.resources.action_download
import kreate.resources.generated.resources.action_like_dislike
import kreate.resources.generated.resources.action_none
import kreate.resources.generated.resources.action_play_next
import kreate.resources.generated.resources.action_remove_from_queue
import kreate.resources.generated.resources.add_to_queue
import kreate.resources.generated.resources.blank
import kreate.resources.generated.resources.delete
import kreate.resources.generated.resources.download
import kreate.resources.generated.resources.download_progress
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.heart_dislike
import kreate.resources.generated.resources.heart_outline
import kreate.resources.generated.resources.skip_next
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class QueueSwipeAction(
    override val iconId: DrawableResource,
    override val textId: StringResource
): Drawable, TextView {

    NoAction(Res.drawable.blank, Res.string.action_none),

    PlayNext(Res.drawable.skip_next, Res.string.action_play_next),

    Download(Res.drawable.download, Res.string.action_download),

    Favourite(Res.drawable.favorite_filled, Res.string.action_like_dislike),

    RemoveFromQueue(Res.drawable.delete, Res.string.action_remove_from_queue),

    Enqueue(Res.drawable.add_to_queue, Res.string.action_add_to_queue);

    @OptIn(UnstableApi::class)
    fun getStateIcon( likeState: Boolean?, downloadState: Int, downloadedStateMedia: DownloadedStateMedia ): DrawableResource =
        when( this ) {
            Download -> when( downloadedStateMedia ) {
                DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> when (downloadState) {
                    androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
                    androidx.media3.exoplayer.offline.Download.STATE_QUEUED,
                    androidx.media3.exoplayer.offline.Download.STATE_RESTARTING -> Res.drawable.download_progress
                    else -> downloadedStateMedia.iconId
                }
                else -> downloadedStateMedia.iconId
            }
            Favourite -> when( likeState ) {
                false -> Res.drawable.heart_dislike
                null  -> Res.drawable.heart_outline
                else  -> Res.drawable.favorite_filled
            }
            else -> iconId
        }
}
