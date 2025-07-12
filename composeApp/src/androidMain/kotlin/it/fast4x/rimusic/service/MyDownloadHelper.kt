package it.fast4x.rimusic.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.kreate.android.Preferences
import app.kreate.android.service.createDataSourceFactory
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import it.fast4x.rimusic.models.Song
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
import kotlin.io.path.createTempDirectory

@UnstableApi
object MyDownloadHelper {
    private val executor = Executors.newCachedThreadPool()
    private val coroutineScope = CoroutineScope(
        executor.asCoroutineDispatcher() +
                SupervisorJob() +
                CoroutineName("MyDownloadService-Executor-Scope")
    )

    // While the class is not a singleton (lifecycle), there should only be one download state at a time
//    private val mutableDownloadState = MutableStateFlow(false)
//    val downloadState = mutableDownloadState.asStateFlow()
//    private val downloadQueue =
//        Channel<DownloadManager>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    const val CACHE_DIRNAME = "exo_downloads"

    private lateinit var databaseProvider: DatabaseProvider
    lateinit var downloadCache: Cache

    private lateinit var downloadNotificationHelper: DownloadNotificationHelper
    private lateinit var downloadManager: DownloadManager
    lateinit var audioQualityFormat: AudioQualityFormat


    var downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    fun getDownload(songId: String): Flow<Download?> {
        return downloads.map { it[songId] }

    }

    @SuppressLint("LongLogTag")
    @Synchronized
    fun getDownloads() {
        val result = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }
        downloads.value = result

    }


    @Synchronized
    fun getDownloadNotificationHelper(context: Context?): DownloadNotificationHelper {
        if (!MyDownloadHelper::downloadNotificationHelper.isInitialized) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context!!, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        ensureDownloadManagerInitialized(context)
        return downloadManager
    }

    /*
        @Synchronized
        fun getDownloadTracker(context: Context): DownloadTracker {
            ensureDownloadManagerInitialized(context)
            return downloadTracker
        }

     */

    @Synchronized
    private fun initDownloadCache( context: Context ): SimpleCache {
        val cacheSize by Preferences.SONG_DOWNLOAD_SIZE

        val cacheEvictor = when( cacheSize ) {
            ExoPlayerDiskDownloadCacheMaxSize.Unlimited -> NoOpCacheEvictor()
            else                                        -> LeastRecentlyUsedCacheEvictor( cacheSize.bytes )
        }

        val cacheDir = when( cacheSize ) {
            // Temporary directory deletes itself after close
            // It means songs remain on device as long as it's open
            ExoPlayerDiskDownloadCacheMaxSize.Disabled -> createTempDirectory( CACHE_DIRNAME ).toFile()

            else                               ->
                // Looks a bit ugly but what it does is
                // check location set by user and return
                // appropriate path with [CACHE_DIRNAME] appended.
                when( Preferences.EXO_CACHE_LOCATION.value ) {
                    ExoPlayerCacheLocation.System  -> context.cacheDir
                    ExoPlayerCacheLocation.Private -> context.filesDir
                }.resolve( CACHE_DIRNAME )
        }

        // Ensure this location exists
        cacheDir.mkdirs()

        return SimpleCache( cacheDir, cacheEvictor, getDatabaseProvider(context) )
    }

    @Synchronized
    fun getDownloadCache( context: Context ): Cache {
        if ( !MyDownloadHelper::downloadCache.isInitialized )
            downloadCache = initDownloadCache( context )

        return downloadCache
    }

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        audioQualityFormat = Preferences.AUDIO_QUALITY.value

        if (!MyDownloadHelper::downloadManager.isInitialized) {
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getDownloadCache(context),
                createDataSourceFactory(),
                executor
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 2
                requirements = Requirements(Requirements.NETWORK)

                addListener(
                    object : DownloadManager.Listener {

                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?
                        ) = run {
                            syncDownloads(download)
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download
                        ) = run {
                            syncDownloads(download)
                        }
                    }
                )
            }

            //downloadTracker =
            //    DownloadTracker(context, getHttpDataSourceFactory(context), downloadManager)
        }
    }

    @Synchronized
    private fun syncDownloads(download: Download) {
        downloads.update { map ->
            map.toMutableMap().apply {
                set(download.request.id, download)
            }
        }
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (!MyDownloadHelper::databaseProvider.isInitialized) databaseProvider =
            StandaloneDatabaseProvider(context)
        return databaseProvider
    }

    fun addDownload(context: Context, mediaItem: MediaItem) {
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
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .data(imageUrl)
                    .size(1200)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .allowHardware(false)
                    .diskCacheKey(imageUrl.toString())
                    .build()
            )
        }

    }

    fun removeDownload(context: Context, mediaItem: MediaItem) {
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

    fun resumeDownloads(context: Context) {
        DownloadService.sendResumeDownloads(
            context,
            MyDownloadService::class.java,
            false
        )
    }

    fun autoDownload(context: Context, mediaItem: MediaItem) {
        if ( Preferences.AUTO_DOWNLOAD.value ) {
            if (downloads.value[mediaItem.mediaId]?.state != Download.STATE_COMPLETED)
                addDownload(context, mediaItem)
        }
    }

    fun autoDownloadWhenLiked(context: Context, mediaItem: MediaItem) {
        if ( Preferences.AUTO_DOWNLOAD_ON_LIKE.value ) {
            Database.asyncQuery {
                runBlocking {
                    if( songTable.isLiked( mediaItem.mediaId ).first() )
                        autoDownload(context, mediaItem)
                    else
                        removeDownload(context, mediaItem)
                }
            }
        }
    }

    fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean?, context: Context ) {
        // Only continues when this setting is enabled
        val isSettingEnabled by Preferences.AUTO_DOWNLOAD_ON_LIKE
        if( !isSettingEnabled || !isNetworkConnected( context ) )
            return

        // [likeState] is a tri-state value,
        // only `true` represents like, so
        // `true` must be value set to download
        if( likeState == true )
            autoDownload( context, mediaItem )
        else
            removeDownload( context, mediaItem )
    }

    fun handleDownload(context: Context, song: Song, removeIfDownloaded: Boolean = false ) {
        if( song.isLocal ) return

        val isDownloaded =
            downloads.value.values.any{ it.state == Download.STATE_COMPLETED && it.request.id == song.id }

        if( isDownloaded && removeIfDownloaded )
            removeDownload( context, song.asMediaItem )
        else if( !isDownloaded )
            addDownload( context, song.asMediaItem )
    }
}
