package app.kreate.android.service

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import app.kreate.database.models.Song
import kotlinx.coroutines.flow.StateFlow

@UnstableApi
interface DownloadHelper {

    companion object {

        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    }

    val downloadManager: DownloadManager
    val downloads: StateFlow<Map<String, Download>>

    fun getDownloadNotificationHelper(): DownloadNotificationHelper

    fun addDownload( mediaItem: MediaItem )

    fun removeDownload( mediaItem: MediaItem )

    fun handleDownload( song: Song, removeIfDownloaded: Boolean = false )
}