package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import it.fast4x.rimusic.R
import it.fast4x.rimusic.appContext

enum class PlaylistSwipeAction(
    @field:DrawableRes override val iconId: Int
): Drawable {

    NoAction( R.drawable.close ),

    PlayNext( R.drawable.play_skip_forward ),

    Download( R.drawable.download ),

    Favourite( R.drawable.heart_outline ),

    Enqueue( R.drawable.enqueue );

    val displayName: String
        get() = when (this) {
            NoAction -> appContext().resources.getString(R.string.none)
            PlayNext -> appContext().resources.getString(R.string.play_next)
            Download  -> appContext().resources.getString(R.string.download)
            Favourite -> appContext().resources.getString(R.string.favorites)
            Enqueue  -> appContext().resources.getString(R.string.enqueue)
        }

    @OptIn(UnstableApi::class)
    fun getStateIcon(likedState: Long?, downloadState: Int, downloadedStateMedia: DownloadedStateMedia): Int? {
        return when (this) {
            Download -> when (downloadedStateMedia) {
                DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> when (downloadState) {
                    androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> R.drawable.download_progress
                    androidx.media3.exoplayer.offline.Download.STATE_QUEUED -> R.drawable.download_progress
                    androidx.media3.exoplayer.offline.Download.STATE_RESTARTING -> R.drawable.download_progress
                    else -> downloadedStateMedia.iconId
                }
                else -> downloadedStateMedia.iconId
            }
            Favourite -> when (likedState) {
                -1L -> R.drawable.heart_dislike
                null -> R.drawable.heart_outline
                else -> R.drawable.heart
            }
            else -> iconId
        }
    }
}
