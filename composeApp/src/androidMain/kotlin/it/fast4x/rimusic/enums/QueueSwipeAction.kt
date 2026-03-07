package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class QueueSwipeAction(
    @field:DrawableRes override val androidIconId: Int,
    @field:StringRes override val androidTextId: Int,
): Drawable, TextView {

    NoAction( R.drawable.close, R.string.none ),

    PlayNext( R.drawable.play_skip_forward, R.string.play_next ),

    Download( R.drawable.download, R.string.download ),

    Favourite( R.drawable.heart_outline, R.string.favorites ),

    RemoveFromQueue( R.drawable.trash, R.string.remove_from_queue ),

    Enqueue( R.drawable.enqueue, R.string.enqueue );

    @OptIn(UnstableApi::class)
    fun getStateIcon( likeState: Boolean?, downloadState: Int, downloadedStateMedia: DownloadedStateMedia ): Int? =
        when( this ) {
            NoAction -> null
            Download -> when( downloadedStateMedia ) {
                DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> when (downloadState) {
                    androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> R.drawable.download_progress
                    androidx.media3.exoplayer.offline.Download.STATE_QUEUED -> R.drawable.download_progress
                    androidx.media3.exoplayer.offline.Download.STATE_RESTARTING -> R.drawable.download_progress
                    else -> downloadedStateMedia.androidIconId
                }
                else -> downloadedStateMedia.androidIconId
            }
            Favourite -> when( likeState ) {
                false -> R.drawable.heart_dislike
                null  -> R.drawable.heart_outline
                else  -> R.drawable.heart
            }
            else -> androidIconId
        }
}
