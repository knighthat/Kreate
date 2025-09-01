package it.fast4x.rimusic.utils


import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalDownloadHelper
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.enums.DownloadedStateMedia
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.LOCAL_KEY_PREFIX
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.service.modern.isLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map


@UnstableApi
@Composable
fun downloadedStateMedia(mediaId: String): DownloadedStateMedia {
    val binder = LocalPlayerServiceBinder.current

    val cachedBytes = remember(mediaId) {
        binder?.cache?.getCachedBytes(mediaId, 0, -1)
    }

    val isDownloaded by remember {
        MyDownloadHelper.getDownload( mediaId ).map {
            it?.state == Download.STATE_COMPLETED
        }
    }.collectAsState( false, Dispatchers.IO )
    val isCached by remember {
        Database.formatTable.findBySongId( mediaId ).map {
            it?.contentLength == cachedBytes
        }
    }.collectAsState( false, Dispatchers.IO )

    return when {
        isDownloaded && isCached -> DownloadedStateMedia.CACHED_AND_DOWNLOADED
        isDownloaded && !isCached -> DownloadedStateMedia.DOWNLOADED
        !isDownloaded && isCached -> DownloadedStateMedia.CACHED
        else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
    }
}

@UnstableApi
@Composable
fun getDownloadStateMedia(
    binder: PlayerServiceModern.Binder,
    songId: String
): DownloadedStateMedia {
    if( songId.startsWith( LOCAL_KEY_PREFIX, true ) )
        return DownloadedStateMedia.DOWNLOADED

    val isDownloaded by remember {
        MyDownloadHelper.getDownload( songId )
            .map { it?.state == Download.STATE_COMPLETED }
    }.collectAsState( false, Dispatchers.IO )
    val isCached by remember {
        Database.formatTable
            .findBySongId( songId )
            .map {
                if( it?.contentLength == null )
                    return@map false
                binder.cache.isCached( it.songId, 0, it.contentLength )
            }
    }.collectAsState( false, Dispatchers.IO )

    return when {
        isDownloaded && isCached  -> DownloadedStateMedia.CACHED_AND_DOWNLOADED
        isDownloaded && !isCached -> DownloadedStateMedia.DOWNLOADED
        !isDownloaded && isCached -> DownloadedStateMedia.CACHED
        // !isDownloaded && !isCached
        else                      -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
    }
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