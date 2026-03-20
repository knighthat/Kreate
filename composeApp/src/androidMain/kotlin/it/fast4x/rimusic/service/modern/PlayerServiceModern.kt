package it.fast4x.rimusic.service.modern

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.playback.AudioHandler
import app.kreate.android.service.player.ExoPlayerListener
import app.kreate.android.service.player.LiveWallpaperEngine
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.player.VolumeObserver
import app.kreate.android.utils.isLocalFile
import app.kreate.android.widget.Widget
import app.kreate.database.models.Event
import app.kreate.di.CacheType
import co.touchlab.kermit.Logger
import com.google.common.util.concurrent.MoreExecutors
import io.ktor.client.HttpClient
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.extensions.connectivity.AndroidConnectivityObserverLegacy
import it.fast4x.rimusic.service.BitmapProvider
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.utils.AppLifecycleTracker
import it.fast4x.rimusic.utils.CoilBitmapLoader
import it.fast4x.rimusic.utils.collect
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.discord.Discord
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds


val MediaItem.isLocal get() = localConfiguration?.uri?.isLocalFile() ?: false


@androidx.annotation.OptIn(UnstableApi::class)
class PlayerServiceModern:
    MediaLibraryService(),
    PlaybackStatsListener.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    Player.Listener,
    KoinComponent
{
    private val cache: Cache by inject(CacheType.CACHE)
    private val discord: Discord by inject()
    private val player: StatefulPlayer by inject()
    private val downloadHelper: DownloadHelper by inject()
    private val volumeObserver: VolumeObserver by inject()
    private val logger = Logger.withTag( this::class.java.simpleName )

    private lateinit var listener: ExoPlayerListener
    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaLibrarySession
    private var mediaLibrarySessionCallback: MediaLibrarySessionCallback =
        MediaLibrarySessionCallback(this)
    private lateinit var bitmapProvider: BitmapProvider
    private lateinit var downloadListener: DownloadManager.Listener
    private lateinit var audioHandler: AudioHandler

    val currentMediaItem = MutableStateFlow<MediaItem?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentSong = currentMediaItem.flatMapLatest { mediaItem ->
        Database.songTable.findById( mediaItem?.mediaId ?: "" )
    }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    var currentSongStateDownload = MutableStateFlow(Download.STATE_STOPPED)

    lateinit var connectivityObserver: AndroidConnectivityObserverLegacy
    private val isNetworkAvailable = MutableStateFlow(true)
    private val waitingForNetwork = MutableStateFlow(false)

    private var liveWallpaperEngine: LiveWallpaperEngine? = null

    private fun registerLiveWallpaperEngine() {
        // Always remove previous instance first (if applicable)
        liveWallpaperEngine?.release()
        liveWallpaperEngine?.also( player::removeListener )

        liveWallpaperEngine = LiveWallpaperEngine(this)
        liveWallpaperEngine?.also( player::addListener )

        logger.d { "LiveWallpaper registered" }
    }

    private suspend fun unregisterLiveWallpaperEngine() {
        liveWallpaperEngine?.restore()
        liveWallpaperEngine?.release()
        withContext( Dispatchers.Main ) {
            liveWallpaperEngine?.also( player::removeListener )
        }

        logger.d { "LiveWallpaper unregistered" }
    }

    private fun onMediaItemTransition( mediaItem: MediaItem? ) {
        listener.updateMediaControl( this, player )

        if( mediaItem != null ) {
            updateBitmap()
            updateDownloadedState()
            updateWidgets()

            if( !Preferences.isLoggedInToDiscord() )
                return

//            val startTime = System.currentTimeMillis() - player.currentPosition
//            discord.updateMediaItem( mediaItem, startTime )
        }
//        else if( Preferences.isLoggedInToDiscord() )
//            discord.stop()
    }

    override fun onStartCommand( intent: Intent?, flags: Int, startId: Int ): Int {
        when( intent?.action ) {
            ACTION_RESTART -> {
                player.pause()
                stopSelf()
            }
            PLAYER_ACTION_PLAY -> player.play()
            PLAYER_ACTION_PAUSE -> player.pause()
            PLAYER_ACTION_NEXT -> player.seekToNext()
            PLAYER_ACTION_PREVIOUS -> player.seekToPrevious()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        Innertube.client = inject<HttpClient>().value

        super.onCreate()

        audioHandler = AudioHandler(this, handler, player)
        volumeObserver.register()

        // Enable Android Auto if disabled, REQUIRE ENABLING DEV MODE IN ANDROID AUTO
        try {
            connectivityObserver.unregister()
        } catch (e: Exception) {
            // isn't registered
        }
        connectivityObserver = AndroidConnectivityObserverLegacy(this@PlayerServiceModern)
        coroutineScope.launch {
            connectivityObserver.networkStatus.collect { isAvailable ->
                isNetworkAvailable.value = isAvailable
                logger.d { "PlayerServiceModern network status: $isAvailable" }
                if (isAvailable && waitingForNetwork.value) {
                    waitingForNetwork.value = false
                    withContext( Dispatchers.Main ) {
                        player.play()
                    }
                }
            }
        }

        DefaultMediaNotificationProvider(this)
            .apply { setSmallIcon( R.drawable.app_icon_monochrome ) }
            .also( ::setMediaNotificationProvider )

        runCatching {
            bitmapProvider = BitmapProvider(
                bitmapSize = (512 * resources.displayMetrics.density).roundToInt(),
                colorProvider = { isSystemInDarkMode ->
                    if (isSystemInDarkMode) Color.BLACK else Color.WHITE
                }
            )
        }.onFailure {
            logger.e( it ) { "Failed init bitmap provider" }
        }

        MyDownloadHelper.instance = this.downloadHelper

        PlaybackStatsListener(false, this@PlayerServiceModern)
            .also( player::addAnalyticsListener )

        preferences.registerOnSharedPreferenceChangeListener(this)
        preferences.registerOnSharedPreferenceChangeListener(audioHandler)

        // Force player to add all commands available, prior to android 13
        val forwardingPlayer =
            object : ForwardingPlayer(player) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands()
                        .buildUpon()
                        .addAllCommands()
                        //.remove(COMMAND_SEEK_TO_PREVIOUS)
                        //.remove(COMMAND_SEEK_TO_NEXT)
                        .build()
                }
            }

        // Build the media library session
        mediaSession =
            MediaLibrarySession.Builder(this, forwardingPlayer, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java)
                            .putExtra("expandPlayerBottomSheet", true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .setBitmapLoader( CoilBitmapLoader(coroutineScope) )
                .build()

        listener = ExoPlayerListener(
            player,
            mediaSession,
            waitingForNetwork,
            ::sendOpenEqualizerIntent,
            ::sendCloseEqualizerIntent,
            ::onMediaItemTransition
        )

        player.addListener( listener )
        player.addListener( this )
        player.addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))

        mediaLibrarySessionCallback.apply {
            listener = this@PlayerServiceModern.listener
        }

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, PlayerServiceModern::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        // Download listener help to notify download change to UI
        downloadListener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) = run {
                if (download.request.id != currentMediaItem.value?.mediaId) return@run
                println("PlayerServiceModern onDownloadChanged current song ${currentMediaItem.value?.mediaId} state ${download.state} key ${download.request.id}")
                updateDownloadedState()
            }
        }
        MyDownloadHelper.instance.downloadManager.addListener(downloadListener)

        // Ensure that song is updated
        currentSong.debounce(1000).collect(coroutineScope) { song ->
            println("PlayerServiceModern onCreate currentSong $song")
            updateDownloadedState()
            println("PlayerServiceModern onCreate currentSongIsDownloaded ${currentSongStateDownload.value}")

            withContext(Dispatchers.Main) {
                updateWidgets()
            }
        }

        /* Queue is saved in events without scheduling it (remove this in future)*/
        // Load persistent queue when start activity and save periodically in background
        if ( Preferences.ENABLE_PERSISTENT_QUEUE.value ) {
            maybeResumePlaybackOnStart()

            val scheduler = Executors.newScheduledThreadPool(1)
            scheduler.scheduleWithFixedDelay({
                println("PlayerServiceModern onCreate savePersistentQueue")
                listener.saveQueueToDatabase()
            }, 0, 30, TimeUnit.SECONDS)

        }

        if( Preferences.isLoggedInToDiscord() ) {
            val token by Preferences.DISCORD_ACCESS_TOKEN
            discord.login( token )
        }
        if( Preferences.LIVE_WALLPAPER.value > 0 )
            registerLiveWallpaperEngine()
    }

    override fun onUpdateNotification( session: MediaSession, startInForegroundRequired: Boolean ) =
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch( err: Exception ) {
            logger.e( err ) { "failed to update notification" }
        }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        // if pause listen history is enabled, don't register statistic event
        if ( Preferences.PAUSE_HISTORY.value ) return

        val mediaItem =
            eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        val totalPlayTimeMs = playbackStats.totalPlayTimeMs

        if ( totalPlayTimeMs > 5000 )
            Database.asyncTransaction {
                songTable.updateTotalPlayTime( mediaItem.mediaId, totalPlayTimeMs, true )
            }


        if ( totalPlayTimeMs <= Preferences.QUICK_PICKS_MIN_DURATION.value.asMillis )
            return

        /*
            There's a really small chance that at this point, the song
            is yet to exist in the database, thus, `FOREIGN KEY constraint failed` is thrown.

            To avoid this, a compact suspendable task is added,
            its job is to wait (maximum 5s) for song to be added,
            if it isn't by then, cancel the run
         */
        CoroutineScope(Dispatchers.IO).launch {
            withTimeoutOrNull( 5.seconds ) {
                Database.songTable
                        .findById( mediaItem.mediaId )
                        .filterNotNull()
                        .first()
            } ?: return@launch

            Database.asyncTransaction {
                eventTable.insertIgnore(
                    Event(
                        songId = mediaItem.mediaId,
                        timestamp = System.currentTimeMillis(),
                        playTime = totalPlayTimeMs
                    )
                )
            }
        }
    }

    @UnstableApi
    override fun onDestroy() {
        runCatching {
            listener.saveQueueToDatabase()
            volumeObserver.unregister()
            runBlocking( Dispatchers.Default ) {
                unregisterLiveWallpaperEngine()
            }

            stopService(intent<MyDownloadService>())
            stopService(intent<PlayerServiceModern>())

            player.removeListener( listener )
            player.stop()
            player.release()

            audioHandler.unregister()
            mediaSession.release()
            cache.release()
            //downloadCache.release()
            MyDownloadHelper.instance.downloadManager.removeListener(downloadListener)

            listener.loudnessEnhancer?.release()

            coroutineScope.cancel()

            runBlocking { discord.logout() }

            preferences.unregisterOnSharedPreferenceChangeListener(this)
        }.onFailure {
            logger.e( it ) { "onDestroy failed!" }
        }
        super.onDestroy()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            Preferences.Key.LIVE_WALLPAPER -> {
                val currentValue by Preferences.LIVE_WALLPAPER
                val newValue = preferences.getInt(key, 0)

                // Topology: only make change if it goes from on to off or vice versa
                if( newValue == 0 && currentValue != 0 )
                    coroutineScope.launch( Dispatchers.Default ) {
                        unregisterLiveWallpaperEngine()
                    }
                else if ( newValue > 0 && currentValue == 0 )
                    registerLiveWallpaperEngine()
            }
        }
    }

    @MainThread
    private fun updateBitmap() {
        with(bitmapProvider) {
            var newUriForLoad = player.currentMediaItem?.mediaMetadata?.artworkUri
            if(lastUri == player.currentMediaItem?.mediaMetadata?.artworkUri) {
                newUriForLoad = null
            }

            load(newUriForLoad) {
                updateWidgets()
            }
        }
    }

    @MainThread
    fun updateWidgets() {
        val isPlaying = player.isPlaying
        val metadata = player.currentMediaItem?.mediaMetadata ?: return
        val actions = Triple(
            if( isPlaying ) player::pause else player::play,
            player::seekToPrevious,
            player::seekToNext
        )

        coroutineScope.launch( Dispatchers.Default ) {
            Widget.Vertical.update( this@PlayerServiceModern, actions, isPlaying, metadata )
            Widget.Horizontal.update( this@PlayerServiceModern, actions, isPlaying, metadata )
        }
    }

    @UnstableApi
    private fun sendOpenEqualizerIntent() {
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }


    @UnstableApi
    private fun sendCloseEqualizerIntent() {
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    private fun maybeResumePlaybackOnStart() {
        if( Preferences.ENABLE_PERSISTENT_QUEUE.value
            && Preferences.RESUME_PLAYBACK_ON_STARTUP.value
            && AppLifecycleTracker.isInForeground()
        ) player.play()
    }

    fun updateDownloadedState() {
        if (currentSong.value == null) return
        val mediaId = currentSong.value!!.id
        val downloads = MyDownloadHelper.instance.downloads.value
        currentSongStateDownload.value = downloads[mediaId]?.state ?: Download.STATE_STOPPED
        /*
        if (downloads[currentSong.value?.id]?.state == Download.STATE_COMPLETED) {
            currentSongIsDownloaded.value = true
        } else {
            currentSongIsDownloaded.value = false
        }
        */
        println("PlayerServiceModern updateDownloadedState downloads count ${downloads.size} currentSongIsDownloaded ${currentSong.value?.id}")
        listener.updateMediaControl( this@PlayerServiceModern, player )
    }

    companion object {
        const val SleepTimerNotificationId = 1002
        const val SleepTimerNotificationChannelId = "sleep_timer_channel_id"

        val PlayerErrorsToReload = arrayOf(416, 4003)
        val PlayerErrorsToSkip = arrayOf(2000)

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SEARCHED = "searched"
        const val ACTION_RESTART = "restart"
        //<editor-fold desc="Player actions">
        const val PLAYER_ACTION_PLAY = "PLAYER_PLAY"
        const val PLAYER_ACTION_PAUSE = "PLAYER_PAUSE"
        const val PLAYER_ACTION_NEXT = "PLAYER_NEXT"
        const val PLAYER_ACTION_PREVIOUS = "PLAYER_PREVIOUS"
        //</editor-fold>
    }
}