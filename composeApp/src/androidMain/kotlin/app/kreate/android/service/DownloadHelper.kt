package app.kreate.android.service

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import it.fast4x.rimusic.models.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@UnstableApi
interface DownloadHelper {

    companion object {

        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    }

    val downloadManager: DownloadManager
    val downloads: MutableStateFlow<Map<String, Download>>

    fun getDownload( songId: String ): Flow<Download?>

    fun getDownloadNotificationHelper(): DownloadNotificationHelper

    fun addDownload( mediaItem: MediaItem )

    fun removeDownload( mediaItem: MediaItem )

    fun autoDownload( mediaItem: MediaItem )

    fun autoDownloadWhenLiked( mediaItem: MediaItem )

    fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean? )

    fun handleDownload( song: Song, removeIfDownloaded: Boolean = false )
}