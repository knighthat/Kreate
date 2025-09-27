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
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.core.app.NotificationCompat
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
import androidx.media3.exoplayer.ExoPlayer
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
import app.kreate.android.service.Discord
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.newpipe.NewPipeDownloader
import app.kreate.android.service.player.ExoPlayerListener
import app.kreate.android.service.player.VolumeObserver
import app.kreate.android.utils.centerCropBitmap
import app.kreate.android.utils.centerCropToMatchScreenSize
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.widget.Widget
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.PresetsReverb
import it.fast4x.rimusic.enums.WallpaperType
import it.fast4x.rimusic.extensions.connectivity.AndroidConnectivityObserverLegacy
import it.fast4x.rimusic.models.Event
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.BitmapProvider
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.utils.CoilBitmapLoader
import it.fast4x.rimusic.utils.TimerJob
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.broadCastPendingIntent
import it.fast4x.rimusic.utils.collect
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.getEnum
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.playPrevious
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.setGlobalVolume
import it.fast4x.rimusic.utils.timer
import it.fast4x.rimusic.utils.toggleRepeatMode
import it.fast4x.rimusic.utils.toggleShuffleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.impl.DownloadHelperImpl
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import android.os.Binder as AndroidBinder
import me.knighthat.innertube.Innertube as NewInnertube


const val LOCAL_KEY_PREFIX = "local:"

val MediaItem.isLocal get() = mediaId.startsWith(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)

@AndroidEntryPoint
@UnstableApi
class PlayerServiceModern:
    MediaLibraryService(),
    PlaybackStatsListener.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    Player.Listener
{
    @Inject
    lateinit var player: ExoPlayer

    @Inject
    @Named("cache")
    lateinit var cache: Cache
    @Inject
    lateinit var downloadHelper: DownloadHelper

    @RequiresApi(Build.VERSION_CODES.M)
    private val discord: Discord = Discord(this)

    @Inject
    lateinit var volumeObserver: VolumeObserver

    private lateinit var listener: ExoPlayerListener
    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaLibrarySession
    private var mediaLibrarySessionCallback: MediaLibrarySessionCallback =
        MediaLibrarySessionCallback(this, Database, MyDownloadHelper)
    private lateinit var bitmapProvider: BitmapProvider
    private lateinit var downloadListener: DownloadManager.Listener

    private var binder = Binder()

    lateinit var sleepTimer: SleepTimer
    private var timerJob: TimerJob? = null

    val currentMediaItem = MutableStateFlow<MediaItem?>(null)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private val currentSong = currentMediaItem.flatMapLatest { mediaItem ->
        Database.songTable.findById( mediaItem?.mediaId ?: "" )
    }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    var currentSongStateDownload = MutableStateFlow(Download.STATE_STOPPED)

    lateinit var connectivityObserver: AndroidConnectivityObserverLegacy
    private val isNetworkAvailable = MutableStateFlow(true)
    private val waitingForNetwork = MutableStateFlow(false)

    private var notificationManager: NotificationManager? = null

    private lateinit var notificationActionReceiver: NotificationActionReceiver

    private fun onMediaItemTransition( mediaItem: MediaItem? ) {
        updateBitmap()
        listener.updateMediaControl( this, player )
        updateDownloadedState()
        updateWidgets()

        mediaItem?.also {
            if( !isAtLeastAndroid6 || !Preferences.DISCORD_LOGIN.value ) return@also

            val startTime = System.currentTimeMillis() - player.currentPosition
            discord.updateMediaItem( mediaItem, startTime )
        }

        if( mediaItem == null && isAtLeastAndroid6 && Preferences.DISCORD_LOGIN.value )
            discord.stop()
    }


    @kotlin.OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        NewPipe.init( NewPipeDownloader() )

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
                Timber.d("PlayerServiceModern network status: $isAvailable")
                println("PlayerServiceModern network status: $isAvailable")
                if (isAvailable && waitingForNetwork.value) {
                    waitingForNetwork.value = false
                    withContext( Dispatchers.Main ) {
                        binder.gracefulPlay()
                    }
                }
            }
        }

        DefaultMediaNotificationProvider(this)
            .apply { setSmallIcon( R.drawable.ic_launcher_monochrome ) }
            .also( ::setMediaNotificationProvider )

        runCatching {
            bitmapProvider = BitmapProvider(
                bitmapSize = (512 * resources.displayMetrics.density).roundToInt(),
                colorProvider = { isSystemInDarkMode ->
                    if (isSystemInDarkMode) Color.BLACK else Color.WHITE
                }
            )
        }.onFailure {
            Timber.e("Failed init bitmap provider in PlayerService ${it.stackTraceToString()}")
        }

        val preferences = preferences
        MyDownloadHelper.instance = this.downloadHelper

        sleepTimer = SleepTimer(coroutineScope, player).also( player::addListener )

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

        mediaLibrarySessionCallback.apply {
            binder = this@PlayerServiceModern.binder
            toggleLike = binder::toggleLike
            toggleDownload = binder::toggleDownload
            toggleRepeat = binder::toggleRepeat
            toggleShuffle = binder::toggleShuffle
            startRadio = {
                player.currentMediaItem?.let( binder::startRadio )
            }
            callPause = binder::gracefulPause
            actionSearch = binder::actionSearch
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
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setBitmapLoader(CoilBitmapLoader(
                    this,
                    coroutineScope,
                    512 * resources.displayMetrics.density.toInt()
                ))
                .build()

        listener = ExoPlayerListener(
            player,
            mediaSession,
            binder,
            isNetworkAvailable,
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

        binder.player.playbackParameters = PlaybackParameters(
            Preferences.AUDIO_SPEED_VALUE.value,
            Preferences.AUDIO_PITCH.value
        )
        binder.player.volume = Preferences.AUDIO_VOLUME.value
        binder.player.setGlobalVolume(binder.player.volume)

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

        if( isAtLeastAndroid6 )
            discord.register()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if( !isAtLeastAndroid6 || !Preferences.DISCORD_LOGIN.value )
            return

        val mediaItem = player.currentMediaItem ?: return
        val startTime = System.currentTimeMillis() - player.currentPosition
        discord.pause( mediaItem, startTime )
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


        val minTimeForEvent by Preferences.QUICK_PICKS_MIN_DURATION

        if ( totalPlayTimeMs > minTimeForEvent.asMillis ) {
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        if ( Preferences.CLOSE_BACKGROUND_JOB_IN_TASK_MANAGER.value ) {
            broadCastPendingIntent<NotificationDismissReceiver>().send()
            this.stopService(this.intent<MyDownloadService>())
            this.stopService(this.intent<PlayerServiceModern>())
            onDestroy()
        }
        super.onTaskRemoved(rootIntent)
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
                Timber.e("PlayerServiceModern onDestroy unregisterReceiver notificationActionReceiver ${e.stackTraceToString()}")
            }


            mediaSession.release()
            cache.release()
            //downloadCache.release()
            MyDownloadHelper.instance.downloadManager.removeListener(downloadListener)

            listener.loudnessEnhancer?.release()

            timerJob?.cancel()
            timerJob = null

            notificationManager?.cancel(NotificationId)
            notificationManager?.cancelAll()
            notificationManager = null

            coroutineScope.cancel()

            if( isAtLeastAndroid6 )
                discord.release()

            preferences.unregisterOnSharedPreferenceChangeListener(this)
        }.onFailure {
            Timber.e("Failed onDestroy in PlayerService ${it.stackTraceToString()}")
        }
        super.onDestroy()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            Preferences.AUDIO_VOLUME_NORMALIZATION.key,
            Preferences.AUDIO_VOLUME_NORMALIZATION_TARGET.key -> listener.maybeNormalizeVolume()

            Preferences.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE.key -> maybeResumePlaybackWhenDeviceConnected()

            Preferences.AUDIO_SKIP_SILENCE.key ->
                player.skipSilenceEnabled = sharedPreferences.getBoolean( key, Preferences.AUDIO_SKIP_SILENCE.defaultValue )

            Preferences.QUEUE_LOOP_TYPE.key ->
                player.repeatMode = sharedPreferences.getEnum( key, Preferences.QUEUE_LOOP_TYPE.defaultValue ).type

            Preferences.AUDIO_BASS_BOOST_LEVEL.key,
            Preferences.AUDIO_BASS_BOOSTED.key -> maybeBassBoost()

            Preferences.AUDIO_REVERB_PRESET.key -> maybeReverb()
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

    private fun updateWallpaper( bitmap: Bitmap ) {
        val type by Preferences.LIVE_WALLPAPER
        if( type == WallpaperType.DISABLED ) return

        coroutineScope.launch( Dispatchers.Default ) {
            val mgr = WallpaperManager.getInstance( this@PlayerServiceModern )
            val cropRect = with( bitmap ) { centerCropToMatchScreenSize( width, height ) }

            if( isAtLeastAndroid7 ) {
                val flag = when( type ) {
                    WallpaperType.BOTH          -> FLAG_LOCK or FLAG_SYSTEM
                    WallpaperType.LOCKSCREEN    -> FLAG_LOCK
                    WallpaperType.HOME          -> FLAG_SYSTEM
                    // This is intended, [WallpaperType.DISABLED] must not present at this point
                    WallpaperType.DISABLED      -> throw UnsupportedOperationException("WallpaperType.DISABLED is used")
                }

                mgr.setBitmap( bitmap, cropRect, true, flag )
            } else if( type != WallpaperType.LOCKSCREEN )
                mgr.setBitmap( centerCropBitmap( bitmap, cropRect ) )
        }
    }

    @MainThread
    private fun updateBitmap() {
        with(bitmapProvider) {
            var newUriForLoad = binder.player.currentMediaItem?.mediaMetadata?.artworkUri
            if(lastUri == binder.player.currentMediaItem?.mediaMetadata?.artworkUri) {
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
            binder.player.mediaMetadata.title.toString(),
            binder.player.mediaMetadata.artist.toString(),
            binder.player.isPlaying
        )

        val actions = Triple(
            if( status.third ) binder::gracefulPause else binder::gracefulPlay,
            binder.player::seekToPrevious,
            binder.player::seekToNext
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

    private fun actionSearch() {
        binder.actionSearch()
    }

    private fun maybeResumePlaybackOnStart() {
        if( Preferences.ENABLE_PERSISTENT_QUEUE.value
            && Preferences.RESUME_PLAYBACK_ON_STARTUP.value
        ) binder.gracefulPlay()
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestorePlayerQueue() {
        if ( !Preferences.ENABLE_PERSISTENT_QUEUE.value ) return

        Database.asyncQuery {
            val queuedSong = runBlocking {
                queueTable.all().first()
            }

            if (queuedSong.isEmpty()) return@asyncQuery

            val index = queuedSong.indexOfFirst { it.position != null }.coerceAtLeast(0)

            runBlocking(Dispatchers.Main) {
                player.setMediaItems(
                    queuedSong.map { mediaItem ->
                        mediaItem.mediaItem.buildUpon()
                            .setUri(mediaItem.mediaItem.mediaId)
                            .setCustomCacheKey(mediaItem.mediaItem.mediaId)
                            .build().apply {
                                mediaMetadata.extras?.putBoolean("isFromPersistentQueue", true)
                            }
                    },
                    index,
                    queuedSong[index].position ?: C.TIME_UNSET
                )
                player.prepare()
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

    class NotificationDismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            kotlin.runCatching {
                context.stopService(context.intent<MyDownloadService>())
            }.onFailure {
                Timber.e("Failed NotificationDismissReceiver stopService in PlayerServiceModern (MyDownloadService) ${it.stackTraceToString()}")
            }
            kotlin.runCatching {
                context.stopService(context.intent<PlayerServiceModern>())
            }.onFailure {
                Timber.e("Failed NotificationDismissReceiver stopService in PlayerServiceModern (PlayerServiceModern) ${it.stackTraceToString()}")
            }
        }
    }

    inner class NotificationActionReceiver(private val player: Player) : BroadcastReceiver() {
        @ExperimentalCoroutinesApi
        @FlowPreview
        override fun onReceive(context: Context, intent: Intent) {
            when ( intent.action ) {
                Action.pause.value      -> binder::gracefulPause
                Action.play.value       -> binder::gracefulPlay
                Action.next.value       -> player::playNext
                Action.previous.value   -> player::playPrevious
                Action.like.value       -> mediaLibrarySessionCallback::toggleLike
                Action.download.value   -> mediaLibrarySessionCallback::toggleDownload
                Action.playradio.value  -> mediaLibrarySessionCallback.startRadio
                Action.shuffle.value    -> mediaLibrarySessionCallback::toggleShuffle
                Action.search.value     -> mediaLibrarySessionCallback::actionSearch
                Action.repeat.value     -> mediaLibrarySessionCallback::toggleRepeat
            }
        }
    }

    open inner class Binder : AndroidBinder() {
        val service: PlayerServiceModern
            get() = this@PlayerServiceModern

        /*
        fun setBitmapListener(listener: ((Bitmap?) -> Unit)?) {
            bitmapProvider.listener = listener
        }

        */
        val bitmap: Bitmap
            get() = bitmapProvider.bitmap


        val player: ExoPlayer
            get() = this@PlayerServiceModern.player

        val cache: Cache
            get() = this@PlayerServiceModern.cache

        val downloadCache: Cache
            get() = (this@PlayerServiceModern.downloadHelper as DownloadHelperImpl).downloadCache

        val sleepTimerMillisLeft: StateFlow<Long?>?
            get() = timerJob?.millisLeft

        fun startSleepTimer(delayMillis: Long) {
            timerJob?.cancel()



            timerJob = coroutineScope.timer(delayMillis) {
                val notification = NotificationCompat
                    .Builder(this@PlayerServiceModern, SleepTimerNotificationChannelId)
                    .setContentTitle(getString(R.string.sleep_timer_ended))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(true)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .build()

                notificationManager?.notify(SleepTimerNotificationId, notification)

                stopSelf()
                exitProcess(0)
            }
        }

        fun cancelSleepTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        private var radioJob: Job? = null

        var isLoadingRadio by mutableStateOf(false)
            private set

        /**
         * Contains 2 major steps:
         * 1. Fetch YouTube Music for **playlistId** of this song
         * 2. Use said **playlistId** to get more songs
         *
         * **_playlistId_** isn't the playlist this song belongs to,
         * but rather the "mood", "style", or "vibe" matches this song.
         */
        fun startRadio(
            mediaItem: MediaItem,
            append: Boolean = false,
            endpoint: NavigationEndpoint.Endpoint.Watch? = null
        ) {
            this.stopRadio()

            // Play song immediately while other songs are being loaded
            if( player.currentMediaItem?.mediaId != mediaItem.mediaId )
                player.forcePlay( mediaItem )

            // Prevent UI from freezing up while data is being fetched
            radioJob = coroutineScope.launch {
                isLoadingRadio = true

                NewInnertube.radio(
                    mediaItem.mediaId,
                    CURRENT_LOCALE,
                    endpoint?.playlistId ?: "RDAMVM${mediaItem.mediaId}",
                    endpoint?.params
                ).onSuccess { relatedSongs ->
                    CoroutineScope(Dispatchers.IO ).launch {
                        relatedSongs.fastForEach {
                            Database.upsert( it )
                        }
                    }

                    // Any call to [player] must happen on Main thread
                    val currentQueue = withContext( Dispatchers.Main ) {
                        player.mediaItems.fastMap( MediaItem::mediaId )
                    }

                    // Songs with the same id as provided [Song] should be removed.
                    // The song usually lives at the the first index, but this
                    // way is safer to implement, as it can live through changes in position.
                    relatedSongs.dropWhile { it.id == mediaItem.mediaId || it.id in currentQueue }
                                .fastMap( InnertubeSong::toMediaItem )
                                .also {
                                    // Any call to [player] must happen on Main thread
                                    withContext( Dispatchers.Main ) {
                                        /*
                                            There are 2 possible outcomes when append is not enabled.
                                            User starts radio on currently playing song,
                                            or on a completely different song.

                                            When radio is activated on the same song, remain position
                                            of currently playing song, delete next songs, and append
                                            it with new songs.

                                            When new song is used for radio, replace entire queue with new songs.
                                          */
                                        val curIndex = player.currentMediaItemIndex
                                        val endIndex = player.mediaItemCount
                                        if( !append && player.mediaItemCount > 1 ) {
                                            player.moveMediaItem( curIndex, 0 )
                                            player.removeMediaItems( curIndex + 1, endIndex )
                                        }

                                        player.addMediaItems(it)
                                    }
                                }
                }.onFailure {
                    it.printStackTrace()
                    it.message?.also( Toaster::e )
                }

                isLoadingRadio = false
            }
        }

        fun startRadio(
            song: Song,
            append: Boolean = false,
            endpoint: NavigationEndpoint.Endpoint.Watch? = null
        ) = startRadio( song.asMediaItem, append, endpoint )

        fun stopRadio() {
            isLoadingRadio = false
            radioJob?.cancel()
        }

        /**
         * Pause with fade out effect
         */
        @MainThread
        fun gracefulPause() = player.pause()

        /**
         * Start playing with fade in effect
         */
        @MainThread
        fun gracefulPlay() = player.play()

        /**
         * This method should ONLY be called when the application (sc. activity) is in the foreground!
         */
        fun restartForegroundOrStop() {
            player.pause()
            stopSelf()
        }

        @kotlin.OptIn(FlowPreview::class)
        fun toggleLike() {
            Database.asyncTransaction {
                currentSong.value?.let {
                    songTable.rotateLikeState( it.id )
                }.also {
                    currentSong.debounce(1000).collect(coroutineScope) {
                        listener.updateMediaControl( this@PlayerServiceModern, player )
                    }
                }
            }

            currentSong.value
                ?.let { MyDownloadHelper.autoDownloadWhenLiked(it.asMediaItem) }
        }

        fun toggleDownload() {
            println("PlayerServiceModern toggleDownload currentMediaItem ${currentMediaItem.value} currentSongIsDownloaded ${currentSongStateDownload.value}")
            manageDownload(
                context = this@PlayerServiceModern,
                mediaItem = currentMediaItem.value ?: return,
                downloadState = currentSongStateDownload.value == Download.STATE_COMPLETED
            )
        }

        fun toggleRepeat() {
            player.toggleRepeatMode()
            listener.updateMediaControl( this@PlayerServiceModern, player )
        }

        fun toggleShuffle() {
            player.toggleShuffleMode()
            listener.updateMediaControl( this@PlayerServiceModern, player )
        }

        fun actionSearch() {
            startActivity(Intent(applicationContext, MainActivity::class.java)
                .setAction(MainActivity.action_search)
                .setFlags(FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TASK))
            println("PlayerServiceModern actionSearch")
        }
    }

    @JvmInline
    value class Action(val value: String) {
        val pendingIntent: PendingIntent
            get() = PendingIntent.getBroadcast(
                appContext(),
                100,
                Intent(value).setPackage(appContext().packageName),
                PendingIntent.FLAG_UPDATE_CURRENT.or(if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0)
            )

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

        const val CACHE_DIRNAME = "exo_cache"
    }

}