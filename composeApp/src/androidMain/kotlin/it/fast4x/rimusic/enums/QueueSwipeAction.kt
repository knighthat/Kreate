package it.fast4x.rimusic.enums

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.add_to_queue
import kreate.resources.generated.resources.blank
import kreate.resources.generated.resources.delete
import kreate.resources.generated.resources.download
import kreate.resources.generated.resources.download_progress
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.heart_dislike
import kreate.resources.generated.resources.heart_outline
import kreate.resources.generated.resources.skip_next
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource

enum class QueueSwipeAction(
    override val iconId: DrawableResource,
    @field:StringRes override val androidTextId: Int,
): Drawable, TextView {

    NoAction(Res.drawable.blank, R.string.none),

    PlayNext(Res.drawable.skip_next, R.string.play_next),

    Download(Res.drawable.download, R.string.download),

    Favourite(Res.drawable.favorite_filled, R.string.favorites),

    RemoveFromQueue(Res.drawable.delete, R.string.remove_from_queue),

    Enqueue(Res.drawable.add_to_queue, R.string.enqueue);

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
