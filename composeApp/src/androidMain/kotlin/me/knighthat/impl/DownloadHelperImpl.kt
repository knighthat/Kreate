package me.knighthat.impl

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.DownloadHelper
import app.kreate.database.Database
import app.kreate.database.insertIgnore
import app.kreate.database.models.Song
import app.kreate.player.download.MediaDownloader
import app.kreate.util.thumbnail
import app.kreate.utils.Toaster
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.downloadSyncedLyrics
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors


@OptIn(UnstableApi::class)
class DownloadHelperImpl(
    private val context: Context,
): DownloadHelper, KoinComponent {

    companion object {

        private const val NUM_PARALLEL_DOWNLOADS = 3
        private const val NUM_RETRIES = 2
        private const val EXECUTOR_NAME = "DownloadHelper-Executor-Scope"
    }

    private val executor = Executors.newCachedThreadPool()
    private val coroutineScope = CoroutineScope(
        executor.asCoroutineDispatcher() +
                SupervisorJob() +
                CoroutineName(EXECUTOR_NAME)
    )
    private val downloader: MediaDownloader by inject()

    override val downloadManager: DownloadManager by inject()
    override val downloads = downloader.downloads

    private lateinit var downloadNotificationHelper: DownloadNotificationHelper

    override fun getDownloadNotificationHelper(): DownloadNotificationHelper {
        if (!::downloadNotificationHelper.isInitialized) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context, DownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper
    }

    override fun addDownload( mediaItem: MediaItem ) {
        if (mediaItem.isLocal) return

        if( !isNetworkConnected( context ) ) {
            Toaster.noInternet()
            return
        }

        Database.asyncTransaction {
            insertIgnore( mediaItem )
        }

        val imageUrl = mediaItem.mediaMetadata.artworkUri.thumbnail(1200)

//            sendAddDownload(
//                context,MyDownloadService::class.java,downloadRequest,false
//            )

        coroutineScope.launch {
            downloader.download( mediaItem )
            downloadSyncedLyrics( mediaItem.asSong )

            ImageFactory.requestBuilder( imageUrl.toString() ) {
                bitmapConfig( Bitmap.Config.ARGB_8888 )
                allowHardware( false )
            }
        }
    }

    override fun removeDownload( mediaItem: MediaItem ) {
        if( !mediaItem.isLocal )
            downloader.remove( mediaItem )
    }

    override fun handleDownload( song: Song, removeIfDownloaded: Boolean ) {
        if( song.isLocal ) return

        val isDownloaded =
            downloads.value.values.any{ it.state == Download.STATE_COMPLETED && it.request.id == song.id }

        if( isDownloaded && removeIfDownloaded )
            removeDownload( song.asMediaItem )
        else if( !isDownloaded )
            addDownload( song.asMediaItem )
    }
}