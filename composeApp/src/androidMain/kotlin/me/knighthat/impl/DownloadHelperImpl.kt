package me.knighthat.impl

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import app.kreate.android.Preferences
import app.kreate.android.service.DownloadHelper
import app.kreate.coil3.ImageFactory
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.download
import it.fast4x.rimusic.utils.downloadSyncedLyrics
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.removeDownload
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Named


@OptIn(UnstableApi::class)
class DownloadHelperImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("downloadCache") val downloadCache: Cache,
    @Named("downloadDataSource") dataSourceFactory: DataSource.Factory
): DownloadHelper {

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

    override val downloads: MutableStateFlow<Map<String, Download>>
    override val downloadManager by lazy {
        val listener = object: DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) = syncDownloads(download)

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) = syncDownloads(download)
        }

        val manager = DownloadManager(
            context,
            StandaloneDatabaseProvider(context),
            downloadCache,
            dataSourceFactory,
            executor
        )

        manager.maxParallelDownloads = NUM_PARALLEL_DOWNLOADS
        manager.minRetryCount = NUM_RETRIES
        manager.requirements = Requirements(Requirements.NETWORK)
        manager.addListener( listener )

        manager
    }

    private lateinit var downloadNotificationHelper: DownloadNotificationHelper

    init {
        val results = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while ( cursor.moveToNext() ) {
            results[cursor.download.request.id] = cursor.download
        }
        downloads = MutableStateFlow(results)
    }

    @Synchronized
    private fun syncDownloads( download: Download ) =
        downloads.update { map ->
            map.toMutableMap().apply {
                set(download.request.id, download)
            }
        }

    override fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

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

        val downloadRequest = DownloadRequest
            .Builder(
                /* id      = */ mediaItem.mediaId,
                /* uri     = */ mediaItem.requestMetadata.mediaUri
                    ?: Uri.parse("https://music.youtube.com/watch?v=${mediaItem.mediaId}")
            )
            .setCustomCacheKey(mediaItem.mediaId)
            .setData("${mediaItem.mediaMetadata.artist.toString()} - ${mediaItem.mediaMetadata.title.toString()}".encodeToByteArray()) // Title in notification
            .build()

        Database.asyncTransaction {
            insertIgnore( mediaItem )
        }

        val imageUrl = mediaItem.mediaMetadata.artworkUri.thumbnail(1200)

//            sendAddDownload(
//                context,MyDownloadService::class.java,downloadRequest,false
//            )

        coroutineScope.launch {
            context.download<MyDownloadService>(downloadRequest).exceptionOrNull()?.let {
                if (it is CancellationException) throw it

                Timber.e("MyDownloadHelper scheduleDownload exception ${it.stackTraceToString()}")
                println("MyDownloadHelper scheduleDownload exception ${it.stackTraceToString()}")
            }
            downloadSyncedLyrics( mediaItem.asSong )

            ImageFactory.requestBuilder( imageUrl.toString() ) {
                bitmapConfig( Bitmap.Config.ARGB_8888 )
                allowHardware( false )
            }
        }
    }

    override fun removeDownload( mediaItem: MediaItem ) {
        if (mediaItem.isLocal) return

        //sendRemoveDownload(context,MyDownloadService::class.java,mediaItem.mediaId,false)
        coroutineScope.launch {
            context.removeDownload<MyDownloadService>(mediaItem.mediaId).exceptionOrNull()?.let {
                if (it is CancellationException) throw it

                Timber.e(it.stackTraceToString())
                println("MyDownloadHelper removeDownload exception ${it.stackTraceToString()}")
            }
        }
    }

    override fun autoDownload( mediaItem: MediaItem ) {
        if ( Preferences.AUTO_DOWNLOAD.value ) {
            if (downloads.value[mediaItem.mediaId]?.state != Download.STATE_COMPLETED)
                addDownload(mediaItem)
        }
    }

    override fun autoDownloadWhenLiked( mediaItem: MediaItem ) {
        if ( Preferences.AUTO_DOWNLOAD_ON_LIKE.value ) {
            Database.asyncQuery {
                runBlocking {
                    if( songTable.isLiked( mediaItem.mediaId ).first() )
                        autoDownload(mediaItem)
                    else
                        removeDownload(mediaItem)
                }
            }
        }
    }

    override fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean? ) {
        // Only continues when this setting is enabled
        val isSettingEnabled by Preferences.AUTO_DOWNLOAD_ON_LIKE
        if( !isSettingEnabled || !isNetworkConnected( context ) )
            return

        // [likeState] is a tri-state value,
        // only `true` represents like, so
        // `true` must be value set to download
        if( likeState == true )
            autoDownload( mediaItem)
        else
            removeDownload( mediaItem)
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