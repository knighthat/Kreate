package me.knighthat.impl

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import app.kreate.android.service.DownloadHelper
import app.kreate.database.Database
import app.kreate.database.insertIgnore
import app.kreate.database.models.Song
import app.kreate.player.download.DownloadListener
import app.kreate.player.download.MediaDownloader
import app.kreate.utils.Toaster
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.isNetworkConnected
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@OptIn(UnstableApi::class)
class DownloadHelperImpl(
    private val context: Context,
): DownloadHelper, KoinComponent {

    private val downloader: MediaDownloader by inject()

    override val downloadManager: DownloadManager by inject()
    override val downloads = downloader.downloads

    private lateinit var downloadNotificationHelper: DownloadNotificationHelper

    init {
        downloadManager.addListener( DownloadListener() )
    }

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

        downloader.download( mediaItem )
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