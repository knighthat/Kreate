package it.fast4x.rimusic.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.kreate.android.service.DownloadHelper
import it.fast4x.rimusic.models.Song
import me.knighthat.impl.DownloadHelperImpl

@UnstableApi
object MyDownloadHelper {

    lateinit var instance: DownloadHelper

    val downloads
        get() = instance.downloads

    fun getDownload( songId: String ) = instance.getDownload( songId )

    @Synchronized
    fun getDownloads() = instance.getDownloads()

    @Synchronized
    fun getDownloadNotificationHelper(context: Context?) = instance.getDownloadNotificationHelper()

    @Synchronized
    fun getDownloadManager(context: Context) = instance.downloadManager

    @Synchronized
    fun getDownloadCache( context: Context ) = (instance as DownloadHelperImpl).downloadCache

    fun addDownload(context: Context, mediaItem: MediaItem) = instance.addDownload( mediaItem )

    fun removeDownload(context: Context, mediaItem: MediaItem) = instance.removeDownload( mediaItem )

    fun autoDownload(context: Context, mediaItem: MediaItem) = instance.autoDownload( mediaItem )

    fun autoDownloadWhenLiked(context: Context, mediaItem: MediaItem) = instance.autoDownloadWhenLiked( mediaItem )

    fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean?, context: Context ) = instance.downloadOnLike( mediaItem, likeState )

    fun handleDownload( context: Context, song: Song, removeIfDownloaded: Boolean = false ) = instance.handleDownload( song, removeIfDownloaded )

    fun handleDownload( context: Context, mediaItem: MediaItem, removeIfDownloaded: Boolean = false ) = instance.handleDownload( mediaItem, removeIfDownloaded )
}
