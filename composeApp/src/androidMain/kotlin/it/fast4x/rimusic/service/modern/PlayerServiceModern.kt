package it.fast4x.rimusic.service.modern

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
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
import app.kreate.android.service.player.ExoPlayerListener
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.player.VolumeObserver
import app.kreate.android.utils.centerCropBitmap
import app.kreate.android.utils.centerCropToMatchScreenSize
import app.kreate.android.utils.isLocalFile
import app.kreate.android.widget.Widget
import app.kreate.database.models.Event
import app.kreate.database.models.PersistentQueue
import app.kreate.di.CacheType
import co.touchlab.kermit.Logger
import com.google.common.util.concurrent.MoreExecutors
import io.ktor.client.HttpClient
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.enums.PresetsReverb
import it.fast4x.rimusic.enums.WallpaperType
import it.fast4x.rimusic.extensions.connectivity.AndroidConnectivityObserverLegacy
import it.fast4x.rimusic.service.BitmapProvider
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.utils.AppLifecycleTracker
import it.fast4x.rimusic.utils.CoilBitmapLoader
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.collect
import it.fast4x.rimusic.utils.getEnum
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.playPrevious
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.setGlobalVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds


val MediaItem.isLocal get() = localConfiguration?.uri?.isLocalFile() ?: false

@UnstableApi
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
        MediaLibrarySessionCallback(this, Database, MyDownloadHelper)
    private lateinit var bitmapProvider: BitmapProvider
    private lateinit var downloadListener: DownloadManager.Listener

    val currentMediaItem = MutableStateFlow<MediaItem?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentSong = currentMediaItem.flatMapLatest { mediaItem ->
        Database.songTable.findById( mediaItem?.mediaId ?: "" )
    }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    var currentSongStateDownload = MutableStateFlow(Download.STATE_STOPPED)

    lateinit var connectivityObserver: AndroidConnectivityObserverLegacy
    private val isNetworkAvailable = MutableStateFlow(true)
    private val waitingForNetwork = MutableStateFlow(false)

    private var notificationManager: NotificationManager? = null

    private lateinit var notificationActionReceiver: NotificationActionReceiver

    private var wallpaperRevertJob: Job? = null
    private var wallpaper_cleared: Boolean = false


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
        if( intent?.action == ACTION_RESTART ) {
            player.pause()
            stopSelf()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        Innertube.client = inject<HttpClient>().value

        super.onCreate()

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

        val preferences = preferences
        MyDownloadHelper.instance = this.downloadHelper

        PlaybackStatsListener(false, this@PlayerServiceModern)
            .also( player::addAnalyticsListener )

        preferences.registerOnSharedPreferenceChangeListener(this)

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

        player.skipSilenceEnabled = Preferences.AUDIO_SKIP_SILENCE.value
        player.addListener( listener )
        player.addListener( this )
        player.addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))

        player.repeatMode = Preferences.QUEUE_LOOP_TYPE.value.type

        player.playbackParameters = PlaybackParameters(
            Preferences.AUDIO_SPEED_VALUE.value,
            Preferences.AUDIO_PITCH.value
        )
        player.volume = Preferences.AUDIO_VOLUME.value
        player.setGlobalVolume(player.volume)

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

        notificationActionReceiver = NotificationActionReceiver(player)


        val filter = IntentFilter().apply {
            addAction(Action.play.value)
            addAction(Action.pause.value)
            addAction(Action.next.value)
            addAction(Action.previous.value)
            addAction(Action.like.value)
            addAction(Action.download.value)
            addAction(Action.playradio.value)
            addAction(Action.shuffle.value)
            addAction(Action.repeat.value)
            addAction(Action.search.value)
        }

        ContextCompat.registerReceiver(
            this,
            notificationActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Ensure that song is updated
        currentSong.debounce(1000).collect(coroutineScope) { song ->
            println("PlayerServiceModern onCreate currentSong $song")
            updateDownloadedState()
            println("PlayerServiceModern onCreate currentSongIsDownloaded ${currentSongStateDownload.value}")

            withContext(Dispatchers.Main) {
                updateWidgets()
            }
        }

        maybeRestorePlayerQueue()

        maybeResumePlaybackWhenDeviceConnected()

        maybeBassBoost()

        maybeReverb()

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
    }

    override fun onUpdateNotification( session: MediaSession, startInForegroundRequired: Boolean ) =
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch( err: Exception ) {
            logger.e( err ) { "failed to update notification" }
        }

    override fun onIsPlayingChanged( isPlaying: Boolean ) {
        wallpaperRevertJob?.cancel()


        if (!isPlaying && Preferences.LIVE_WALLPAPER_RESET_DURATION.value != -1L) { // -1 means it should be disabled
            wallpaperRevertJob = coroutineScope.launch {
                delay(Preferences.LIVE_WALLPAPER_RESET_DURATION.value)
                revertWallpaperToDefault()
            }
        } else {
            if (wallpaper_cleared) {
                wallpaper_cleared = false
                updateWallpaper(bitmapProvider.bitmap)
            }
        }
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

            stopService(intent<MyDownloadService>())
            stopService(intent<PlayerServiceModern>())

            player.removeListener( listener )
            player.stop()
            player.release()

            try{
                unregisterReceiver(notificationActionReceiver)
            } catch (e: Exception){
                logger.e( e ) { "onDestroy unregisterReceiver notificationActionReceiver failed!" }
            }


            mediaSession.release()
            cache.release()
            //downloadCache.release()
            MyDownloadHelper.instance.downloadManager.removeListener(downloadListener)

            listener.loudnessEnhancer?.release()

            notificationManager?.cancel(NotificationId)
            notificationManager?.cancelAll()
            notificationManager = null

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
            Preferences.Key.AUDIO_VOLUME_NORMALIZATION,
            Preferences.Key.AUDIO_VOLUME_NORMALIZATION_TARGET -> listener.maybeNormalizeVolume()

            Preferences.Key.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE -> maybeResumePlaybackWhenDeviceConnected()

            Preferences.Key.AUDIO_SKIP_SILENCE ->
                player.skipSilenceEnabled = sharedPreferences.getBoolean( key, Preferences.AUDIO_SKIP_SILENCE.defaultValue )

            Preferences.Key.QUEUE_LOOP_TYPE ->
                player.repeatMode = sharedPreferences.getEnum( key, Preferences.QUEUE_LOOP_TYPE.defaultValue ).type

            Preferences.Key.AUDIO_BASS_BOOST_LEVEL,
            Preferences.Key.AUDIO_BASS_BOOSTED -> maybeBassBoost()

            Preferences.Key.AUDIO_REVERB_PRESET -> maybeReverb()
        }
    }

    private var bassBoost: BassBoost? = null

    private fun maybeBassBoost() {
        if ( !Preferences.AUDIO_BASS_BOOSTED.value ) {
            runCatching {
                bassBoost?.enabled = false
                bassBoost?.release()
            }
            bassBoost = null
            listener.maybeNormalizeVolume()
            return
        }

        runCatching {
            if (bassBoost == null) bassBoost = BassBoost(0, player.audioSessionId)
            val bassboostLevel =
                (Preferences.AUDIO_BASS_BOOST_LEVEL.value * 1000f).toInt().toShort()
            println("PlayerServiceModern maybeBassBoost bassboostLevel $bassboostLevel")
            bassBoost?.enabled = false
            bassBoost?.setStrength(bassboostLevel)
            bassBoost?.enabled = true
        }.onFailure {
            Toaster.e( "Can't enable bass boost" )
        }
    }

    private var reverbPreset: PresetReverb? = null

    private fun maybeReverb() {
        val presetType by Preferences.AUDIO_REVERB_PRESET
        println("PlayerServiceModern maybeReverb presetType $presetType")
        if (presetType == PresetsReverb.NONE) {
            runCatching {
                reverbPreset?.enabled = false
                player.clearAuxEffectInfo()
                reverbPreset?.release()
            }
                reverbPreset = null
            return
        }

        runCatching {
            if (reverbPreset == null) reverbPreset = PresetReverb(1, player.audioSessionId)

            reverbPreset?.enabled = false
            reverbPreset?.preset = presetType.preset
            reverbPreset?.enabled = true
            reverbPreset?.id?.let { player.setAuxEffectInfo(AuxEffectInfo(it, 1f)) }
        }
    }

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private fun maybeResumePlaybackWhenDeviceConnected() {
        if ( !isAtLeastAndroid6 ) return

        if ( Preferences.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE.value ) {
            if (audioManager == null)
                audioManager = getSystemService( AUDIO_SERVICE ) as? AudioManager


            audioDeviceCallback = object : AudioDeviceCallback() {
                private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo): Boolean {
                    if ( !audioDeviceInfo.isSink ) return false

                    return when( audioDeviceInfo.type ) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET        -> true
                        else                                    -> false
                    }
                }

                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    if( player.isPlaying ) return

                    if( addedDevices.any( ::canPlayMusic ) )
                        player.play()
                }
            }

            audioManager?.registerAudioDeviceCallback( audioDeviceCallback, handler )

        } else {
            audioManager?.unregisterAudioDeviceCallback( audioDeviceCallback )
            audioDeviceCallback = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getFlag(type: WallpaperType): Int{
            return when (type) {
                WallpaperType.BOTH -> FLAG_LOCK or FLAG_SYSTEM
                WallpaperType.LOCKSCREEN -> FLAG_LOCK
                WallpaperType.HOME -> FLAG_SYSTEM
                // This is intended, [WallpaperType.DISABLED] must not present at this point
                WallpaperType.DISABLED -> throw UnsupportedOperationException("WallpaperType.DISABLED is used")
            }
    }

    private fun updateWallpaper( bitmap: Bitmap ) {
        val type by Preferences.LIVE_WALLPAPER
        if( type == WallpaperType.DISABLED ) return

        coroutineScope.launch( Dispatchers.Default ) {
            val mgr = WallpaperManager.getInstance( this@PlayerServiceModern )
            val cropRect = with( bitmap ) { centerCropToMatchScreenSize( width, height ) }

            if( isAtLeastAndroid7 ) {
                val flag = getFlag(type)

                mgr.setBitmap( bitmap, cropRect, true, flag )
            } else if( type != WallpaperType.LOCKSCREEN )
                mgr.setBitmap( centerCropBitmap( bitmap, cropRect ) )
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
                updateWallpaper( it )
            }
        }
    }

    @MainThread
    fun updateWidgets() {
        val status = Triple(
            player.mediaMetadata.title.toString(),
            player.mediaMetadata.artist.toString(),
            player.isPlaying
        )

        val actions = Triple(
            if( status.third ) player::pause else player::play,
            player::seekToPrevious,
            player::seekToNext
        )

        CoroutineScope( Dispatchers.IO ).launch {
            // Save bitmap to file
            val file = File( cacheDir, "widget_thumbnail.png" )
            FileOutputStream(file).use { outStream ->
                bitmapProvider.bitmap.compress( Bitmap.CompressFormat.PNG, 50, outStream )
            }

            withContext( Dispatchers.Default ) {
                Widget.Vertical.update( applicationContext, actions, status, file )
                Widget.Horizontal.update( applicationContext, actions, status, file )
            }
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

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestorePlayerQueue() {
        if ( !Preferences.ENABLE_PERSISTENT_QUEUE.value ) return

        CoroutineScope(Dispatchers.IO).launch {
            val queue = Database.queueTable.blockingItems()

            if( queue.isEmpty() ) {
                logger.i { "Persistent queue empty, not resuming!" }
                return@launch
            }

            val startIndex = queue.indexOfFirst { it.position != null }
            val startPositionMs = queue[startIndex].position ?: C.TIME_UNSET
            val mediaItems = withContext( Dispatchers.Default ) {
                queue.map {
                    it.song.asMediaItem.buildUpon().setTag( PersistentQueue.Tag ).build()
                }
            }
            withContext( Dispatchers.Main ) {
                player.setMediaItems( mediaItems, startIndex, startPositionMs )
                player.prepare()
            }
        }
    }


    private fun revertWallpaperToDefault() {
        val type by Preferences.LIVE_WALLPAPER
        if (type == WallpaperType.DISABLED) return
        coroutineScope.launch(Dispatchers.IO) {
            val mgr = WallpaperManager.getInstance(this@PlayerServiceModern)
            try {
                if (isAtLeastAndroid7) {
                    mgr.clear(getFlag(type))
                } else {
                    mgr.clear()
                }
                wallpaper_cleared = true
            } catch (e: IOException) {
                Toaster.e("Failed to revert wallpaper")
            }
        }
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

    inner class NotificationActionReceiver(private val player: StatefulPlayer) : BroadcastReceiver() {
        @ExperimentalCoroutinesApi
        @FlowPreview
        override fun onReceive(context: Context, intent: Intent) {
            when ( intent.action ) {
                Action.pause.value      -> player.pause()
                Action.play.value       -> player.play()
                Action.next.value       -> player.playNext()
                Action.previous.value   -> player.playPrevious()
                Action.like.value       -> mediaLibrarySessionCallback.toggleLike( player )
                Action.download.value   -> player.downloadCurrentMediaItem()
                Action.playradio.value  -> player.startRadio()
                Action.shuffle.value    -> player.toggleShuffleMode()
                Action.search.value     -> mediaLibrarySessionCallback.onSearch()
                Action.repeat.value     -> player.cycleRepeatMode()
            }
        }
    }

    @JvmInline
    value class Action(val value: String) {

        val pendingIntent: PendingIntent
            get() {
                val context: Context by inject(Context::class.java)

                return PendingIntent.getBroadcast(
                    context,
                    100,
                    Intent(value).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT.or(if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0)
                )
            }

        companion object {

            val pause = Action("it.fast4x.rimusic.pause")
            val play = Action("it.fast4x.rimusic.play")
            val next = Action("it.fast4x.rimusic.next")
            val previous = Action("it.fast4x.rimusic.previous")
            val like = Action("it.fast4x.rimusic.like")
            val download = Action("it.fast4x.rimusic.download")
            val playradio = Action("it.fast4x.rimusic.playradio")
            val shuffle = Action("it.fast4x.rimusic.shuffle")
            val search = Action("it.fast4x.rimusic.search")
            val repeat = Action("it.fast4x.rimusic.repeat")

        }
    }

    companion object {
        const val NotificationId = 1001
        const val NotificationChannelId = "default_channel_id"

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

        const val CACHE_DIRNAME = "exo_cache"
    }

}