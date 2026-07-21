package me.knighthat.impl

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.DownloadHelper
import app.kreate.database.Database
import app.kreate.database.insertIgnore
import app.kreate.database.models.Song
import app.kreate.util.thumbnail
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.download
import it.fast4x.rimusic.utils.downloadSyncedLyrics
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.removeDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.knighthat.component.dialog.RestartAppDialog.isActive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds


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

    override val downloadManager: DownloadManager by inject()
    override val downloads = callbackFlow {
        val results = ConcurrentHashMap<String, Download>()
        // Marks "something changed, re-read now" and controls whether we keep polling.
        var busy = downloadManager.currentDownloads.isNotEmpty()

        fun emitSnapshot( downloadManager: DownloadManager ) {
            downloadManager.currentDownloads
                .also {
                    busy = it.isNotEmpty()
                }
                .forEach {
                    results[it.request.id] = it
                }
            trySendBlocking( results.toMap() )
        }

        val listener = object : DownloadManager.Listener {
            override fun onInitialized( downloadManager: DownloadManager ) {
                // On init, populate the map with existing Downloads, including completed ones
                val cursor = downloadManager.downloadIndex.getDownloads()
                while( cursor.moveToNext() ) {
                    results[cursor.download.request.id] = cursor.download
                }

                emitSnapshot( downloadManager )
            }

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) = emitSnapshot( downloadManager )

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) = emitSnapshot( downloadManager )

            override fun onDownloadsPausedChanged(
                downloadManager: DownloadManager,
                downloadsPaused: Boolean,
            ) = emitSnapshot( downloadManager )

            // Fires when the queue drains — our cue to stop polling entirely.
            override fun onIdle( downloadManager: DownloadManager ) = emitSnapshot( downloadManager )
        }
        downloadManager.addListener( listener )

        // The polling loop: active only while work is in flight, otherwise it idles at
        // one wake-up per interval doing a single list check.
        while( isActive ) {
            // TODO: When implement download progress bar, reduce this
            //  value to 250 millis for smoother animation
            delay( 500.milliseconds )
            if( busy ) emitSnapshot( downloadManager )
        }

        awaitClose { downloadManager.removeListener(listener) }
    }.distinctUntilChanged().stateIn( coroutineScope, SharingStarted.Lazily, emptyMap() )

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

        val downloadRequest = DownloadRequest
            .Builder(
                /* id      = */ mediaItem.mediaId,
                /* uri     = */ mediaItem.mediaId.toUri()
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

                Logger.e( it, "DownloadHelperImpl" ) { "addDownload failed!"}
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

                Logger.e( it, "DownloadHelperImpl" ) { "removeDownload failed!"}
            }
        }
    }

    override fun autoDownload( mediaItem: MediaItem ) {
        if ( app.kreate.preferences.Preferences.AUTO_DOWNLOAD.value ) {
            if (downloads.value[mediaItem.mediaId]?.state != Download.STATE_COMPLETED)
                addDownload(mediaItem)
        }
    }

    override fun autoDownloadWhenLiked( mediaItem: MediaItem ) {
        if ( app.kreate.preferences.Preferences.AUTO_DOWNLOAD_ON_LIKE.value ) {
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
        val isSettingEnabled = app.kreate.preferences.Preferences.AUTO_DOWNLOAD_ON_LIKE.value
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