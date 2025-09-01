package it.fast4x.rimusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import app.kreate.android.service.DownloadHelper
import it.fast4x.rimusic.models.Song

@UnstableApi
object MyDownloadHelper {

    lateinit var instance: DownloadHelper

    fun getDownload( songId: String ) = instance.getDownload( songId )

    fun getDownloadNotificationHelper(): DownloadNotificationHelper = instance.getDownloadNotificationHelper()

    fun addDownload(mediaItem: MediaItem) = instance.addDownload( mediaItem )

    fun removeDownload(mediaItem: MediaItem) = instance.removeDownload( mediaItem )

    fun autoDownload(mediaItem: MediaItem) = instance.autoDownload( mediaItem )

    fun autoDownloadWhenLiked(mediaItem: MediaItem) = instance.autoDownloadWhenLiked( mediaItem )

    fun downloadOnLike(mediaItem: MediaItem, likeState: Boolean?) = instance.downloadOnLike( mediaItem, likeState )

    fun handleDownload(song: Song, removeIfDownloaded: Boolean) = instance.handleDownload( song, removeIfDownloaded )
}
