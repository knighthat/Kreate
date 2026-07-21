package app.kreate.internal.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.kreate.player.MediaDownloadService
import app.kreate.player.download.MediaDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap


@OptIn(UnstableApi::class)
internal class MediaDownloaderImpl(
    coroutineScope: CoroutineScope,
    downloadManager: DownloadManager,
    private val context: Context,
) : MediaDownloader {

    override val downloads = callbackFlow {
        val results = ConcurrentHashMap<String, Download>()

        fun emitSnapshot() {
            trySendBlocking( results.toMap() )
        }

        val listener = object : DownloadManager.Listener {
            override fun onInitialized( downloadManager: DownloadManager ) {
                val results = mutableMapOf<String, Download>()
                val cursor = downloadManager.downloadIndex.getDownloads()
                while( cursor.moveToNext() ) {
                    results[cursor.download.request.id] = cursor.download
                }

                emitSnapshot()
            }

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: java.lang.Exception?
            ) {
                results[download.request.id] = download
                emitSnapshot()
            }

            override fun onDownloadRemoved( downloadManager: DownloadManager, download: Download ) {
                results.remove( download.request.id )
                emitSnapshot()
            }
        }
        downloadManager.addListener( listener )

        awaitClose { downloadManager.removeListener(listener) }
    }.distinctUntilChanged().stateIn( coroutineScope, SharingStarted.Eagerly, emptyMap() )

    private fun MediaItem.toDownloadRequest(): DownloadRequest {
        // The title is smuggled through DownloadRequest.data so notifications can show a
        // human-readable name even after process death (the request is what gets persisted).
        val title = mediaMetadata.displayTitle ?: "${mediaMetadata.artist} - ${mediaMetadata.title}"
        val uri = localConfiguration?.uri ?: mediaId.toUri()

        return DownloadRequest.Builder(mediaId, uri)
                              .setMimeType( localConfiguration?.mimeType )
                              .setCustomCacheKey( mediaId )
                              .setData( title.toString().encodeToByteArray() )
                              .build()
    }

    override fun download( mediaItem: MediaItem ) {
        DownloadService.sendAddDownload(
            context,
            MediaDownloadService::class.java,
            mediaItem.toDownloadRequest(),
            // media3 promotes the service itself once work starts
            /* foreground = */ false,
        )
    }

    override fun remove( mediaItem: MediaItem ) {
        DownloadService.sendRemoveDownload(
            context,
            MediaDownloadService::class.java,
            mediaItem.mediaId,
            /* foreground = */ false,
        )
    }
}