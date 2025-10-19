package it.fast4x.rimusic.utils


import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.kreate.android.R
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalDownloadHelper
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.enums.DownloadedStateMedia
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.isLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import me.knighthat.utils.Toaster
import timber.log.Timber


@UnstableApi
@Composable
fun downloadedStateMedia( mediaId: String ): DownloadedStateMedia {
    val isDownloaded by remember {
        MyDownloadHelper.getDownload( mediaId )
                        .map { it?.state == Download.STATE_COMPLETED }
    }.collectAsState( false, Dispatchers.IO )

    // Return early so it doesn't create another remember function
    if( isDownloaded )
        return DownloadedStateMedia.DOWNLOADED

    val cache = LocalPlayerServiceBinder.current?.cache
    try {
        cache!!.cacheSpace
    } catch ( e: Exception ) {
        when( e ) {
            // When cache is uninitialized
            is NullPointerException,
            // When cache is not ready (or is released)
            is IllegalStateException -> { /* Does nothing */ }

            // Except for those 2, which are known (and unimportant) error
            // Must show error to user so they can report
            else -> Toaster.e( R.string.error_access_cache_failed )
        }

        Timber.tag( "DownloadUtil" ).e( e )

        return DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
    }

    // Force cache to to be available
    // Throw NullPointerException is uninitialized
    val isCached by remember( mediaId, cache ) {
        val cachedBytes = cache.getCachedBytes( mediaId, 0, C.LENGTH_UNSET.toLong() )

        Database.formatTable
                .findBySongId( mediaId )
                .map { it?.contentLength == cachedBytes }
    }.collectAsState( false, Dispatchers.IO )

    return if( isCached ) DownloadedStateMedia.CACHED else DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
}

@UnstableApi
fun manageDownload(
    context: android.content.Context,
    mediaItem: MediaItem,
    downloadState: Boolean = false
) {

    if (mediaItem.isLocal) return

    if (downloadState) {
        MyDownloadHelper.removeDownload(mediaItem = mediaItem)
    }
    else {
        if (isNetworkAvailable(context)) {
            MyDownloadHelper.addDownload(mediaItem = mediaItem)
        }
    }

}


@UnstableApi
@Composable
fun getDownloadState(mediaId: String): Int {
    val downloader = LocalDownloadHelper.current
    if (!isNetworkAvailableComposable()) return 3

    return downloader.getDownload(mediaId).collectAsState(initial = null).value?.state
        ?: 3
}

@OptIn(UnstableApi::class)
@Composable
fun isDownloadedSong(mediaId: String): Boolean {
    return when (downloadedStateMedia(mediaId)) {
        DownloadedStateMedia.CACHED -> false
        DownloadedStateMedia.CACHED_AND_DOWNLOADED, DownloadedStateMedia.DOWNLOADED -> true
        else -> false
    }
}