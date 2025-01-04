package it.fast4x.rimusic.service.modern

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.*
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.database.SQLException
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.text.isDigitsOnly
import androidx.media3.common.*
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.*
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.*
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.enums.*
import it.fast4x.rimusic.extensions.audiovolume.AudioVolumeObserver
import it.fast4x.rimusic.extensions.audiovolume.OnAudioVolumeChangedListener
import it.fast4x.rimusic.extensions.discord.sendDiscordPresence
import it.fast4x.rimusic.models.*
import it.fast4x.rimusic.service.BitmapProvider
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.ui.components.themed.SmartMessage
import it.fast4x.rimusic.ui.widgets.PlayerHorizontalWidget
import it.fast4x.rimusic.ui.widgets.PlayerVerticalWidget
import it.fast4x.rimusic.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import android.os.Binder as AndroidBinder


const val LOCAL_KEY_PREFIX = "local:"

@get:OptIn(UnstableApi::class)
val DataSpec.isLocal get() = key?.startsWith(LOCAL_KEY_PREFIX) == true

val MediaItem.isLocal get() = mediaId.startsWith(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)

@UnstableApi
class PlayerServiceModern : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    OnAudioVolumeChangedListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaLibrarySession
    private var mediaLibrarySessionCallback: MediaLibrarySessionCallback =
        MediaLibrarySessionCallback(this, Database, MyDownloadHelper)
    lateinit var player: ExoPlayer
    lateinit var cache: SimpleCache
    lateinit var downloadCache: SimpleCache
    private lateinit var audioVolumeObserver: AudioVolumeObserver
    private lateinit var bitmapProvider: BitmapProvider
    private var volumeNormalizationJob: Job? = null
    private var isPersistentQueueEnabled: Boolean = false
    private var isclosebackgroundPlayerEnabled = false
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private lateinit var downloadListener: DownloadManager.Listener

    var loudnessEnhancer: LoudnessEnhancer? = null
    private var binder = Binder()
    private var showLikeButton = true
    private var showDownloadButton = true

    lateinit var audioQualityFormat: AudioQualityFormat
    lateinit var sleepTimer: SleepTimer
    private var timerJob: TimerJob? = null
    private var radio: YouTubeRadio? = null

    val currentMediaItem = MutableStateFlow<MediaItem?>(null)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)

    private val currentSong = currentMediaItem.flatMapLatest { mediaItem ->
        Database.song(mediaItem?.mediaId)
    }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private val currentFormat = currentMediaItem.flatMapLatest { mediaItem ->
        mediaItem?.mediaId?.let { Database.format(it) }!!
    }

    var currentSongStateDownload = MutableStateFlow(Download.STATE_STOPPED)

    //private lateinit var connectivityManager: ConnectivityManager
    private var notificationManager: NotificationManager? = null
    private val playerVerticalWidget = PlayerVerticalWidget()
    private val playerHorizontalWidget = PlayerHorizontalWidget()

    private lateinit var notificationActionReceiver: NotificationActionReceiver

    @kotlin.OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        // Enable Android Auto if disabled, REQUIRE ENABLING DEV MODE IN ANDROID AUTO
        val component = ComponentName(this, PlayerServiceModern::class.java)
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val notificationType = preferences.getEnum(notificationTypeKey, NotificationType.Default)
        when(notificationType){
            NotificationType.Default -> {
                // DEFAULT NOTIFICATION PROVIDER
                //        setMediaNotificationProvider(
                //            DefaultMediaNotificationProvider(
                //                this,
                //                { NotificationId },
                //                NotificationChannelId,
                //                R.string.player
                //            )
                //            .apply {
                //                setSmallIcon(R.drawable.app_icon)
                //            }
                //        )

                // DEFAULT NOTIFICATION PROVIDER MODDED
                setMediaNotificationProvider(CustomMediaNotificationProvider(this)
                    .apply {
                        setSmallIcon(R.drawable.app_icon)
                    }
                )
            }
            NotificationType.Advanced -> {
                // CUSTOM NOTIFICATION PROVIDER -> CUSTOM NOTIFICATION PROVIDER WITH ACTIONS AND PENDING INTENT
                // ACTUALLY NOT STABLE
                setMediaNotificationProvider(object : MediaNotification.Provider{
                    override fun createNotification(
                        mediaSession: MediaSession,
                        customLayout: ImmutableList<CommandButton>,
                        actionFactory: MediaNotification.ActionFactory,
                        onNotificationChangedCallback: MediaNotification.Provider.Callback
                    ): MediaNotification {
                        return updateCustomNotification(mediaSession)
                    }

                    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean { return false }
                })
            }
        }

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

        preferences.registerOnSharedPreferenceChangeListener(this)

        val preferences = preferences
        isPersistentQueueEnabled = preferences.getBoolean(persistentQueueKey, false)

        audioQualityFormat = preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)
        showLikeButton = preferences.getBoolean(showLikeButtonBackgroundPlayerKey, true)
        showDownloadButton = preferences.getBoolean(showDownloadButtonBackgroundPlayerKey, true)

        val exoPlayerCustomCache = preferences.getInt(exoPlayerCustomCacheKey, 32) * 1000 * 1000L

        val cacheEvictor = when (val size =
            preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> NoOpCacheEvictor()
            ExoPlayerDiskCacheMaxSize.Custom -> LeastRecentlyUsedCacheEvictor(exoPlayerCustomCache)
            else -> LeastRecentlyUsedCacheEvictor(size.bytes)
        }

        //val cacheEvictor = NoOpCacheEvictor()
        val exoPlayerCacheLocation = preferences.getEnum(
            exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System
        )
        val directoryLocation =
            if (exoPlayerCacheLocation == ExoPlayerCacheLocation.Private) filesDir else cacheDir

        var cacheDirName = "rimusic_cache"

        val cacheSize =
            preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)

        if (cacheSize == ExoPlayerDiskCacheMaxSize.Disabled) cacheDirName = "rimusic_no_cache"

        val directory = directoryLocation.resolve(cacheDirName).also { dir ->
            if (dir.exists()) return@also

            dir.mkdir()

            directoryLocation.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.length == 1 && file.name.isDigitsOnly() || file.extension == "uid") {
                    if (!file.renameTo(dir.resolve(file.name))) {
                        file.deleteRecursively()
                    }
                }
            }

            filesDir.resolve("coil").deleteRecursively()
        }

        cache = SimpleCache(directory, cacheEvictor, StandaloneDatabaseProvider(this))
        downloadCache = MyDownloadHelper.getDownloadSimpleCache(applicationContext) as SimpleCache


        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRendersFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                addListener(this@PlayerServiceModern)
                sleepTimer = SleepTimer(coroutineScope, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))
            }

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
            toggleLike = ::toggleLike
            toggleDownload = ::toggleDownload
            toggleRepeat = ::toggleRepeat
            toggleShuffle = ::toggleShuffle
            startRadio = ::startRadio
            callPause = ::callActionPause
            actionSearch = ::actionSearch
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

        player.skipSilenceEnabled = preferences.getBoolean(skipSilenceKey, false)
        player.addListener(this@PlayerServiceModern)
        player.addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))

        player.repeatMode = preferences.getEnum(queueLoopTypeKey, QueueLoopType.Default).type

        binder.player.playbackParameters = PlaybackParameters(
            preferences.getFloat(playbackSpeedKey, 1f),
            preferences.getFloat(playbackPitchKey, 1f)
        )

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, PlayerServiceModern::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        audioVolumeObserver = AudioVolumeObserver(this)
        audioVolumeObserver.register(AudioManager.STREAM_MUSIC, this)

        //connectivityManager = getSystemService()!!

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
        MyDownloadHelper.getDownloadManager(this).addListener(downloadListener)

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

            updateDefaultNotification()
            withContext(Dispatchers.Main) {
                if (song != null) {
                    updateDiscordPresence()
                }
                updateWidgets()
            }
        }

        maybeRestorePlayerQueue()

        maybeResumePlaybackWhenDeviceConnected()

        /* Queue is saved in events without scheduling it (remove this in future)*/
        // Load persistent queue when start activity and save periodically in background
        if (isPersistentQueueEnabled) {
            maybeResumePlaybackOnStart()

            val scheduler = Executors.newScheduledThreadPool(1)
            scheduler.scheduleWithFixedDelay({
                println("PlayerServiceModern onCreate savePersistentQueue")
                maybeSavePlayerQueue()
            }, 0, 30, TimeUnit.SECONDS)

        }


    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        maybeSavePlayerQueue()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateDefaultNotification()
        preferences.edit {
            putEnum(queueLoopTypeKey, QueueLoopType.from(repeatMode))
        }
    }



    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        // if pause listen history is enabled, don't register statistic event
        if (preferences.getBoolean(pauseListenHistoryKey, false)) return

        val mediaItem =
            eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        val totalPlayTimeMs = playbackStats.totalPlayTimeMs

        if (totalPlayTimeMs > 5000) {
            Database.asyncTransaction {
                incrementTotalPlayTimeMs(mediaItem.mediaId, totalPlayTimeMs)
            }
        }


        val minTimeForEvent =
            preferences.getEnum(exoPlayerMinTimeForEventKey, ExoPlayerMinTimeForEvent.`20s`)

        if ( totalPlayTimeMs > minTimeForEvent.asMillis ) {
            Database.asyncTransaction {
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = System.currentTimeMillis(),
                            playTime = totalPlayTimeMs
                        )
                    )
                } catch (e: SQLException) {
                    Timber.e("PlayerService onPlaybackStatsReady SQLException ${e.stackTraceToString()}")
                }
            }

        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        isclosebackgroundPlayerEnabled = preferences.getBoolean(closebackgroundPlayerKey, false)
        if (isclosebackgroundPlayerEnabled) {
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
            maybeSavePlayerQueue()

            preferences.unregisterOnSharedPreferenceChangeListener(this)

            stopService(intent<MyDownloadService>())
            stopService(intent<PlayerServiceModern>())

            player.removeListener(this)
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
            MyDownloadHelper.getDownloadManager(this).removeListener(downloadListener)

            loudnessEnhancer?.release()
            audioVolumeObserver.unregister()

            timerJob?.cancel()
            timerJob = null

            notificationManager?.cancel(NotificationId)
            notificationManager?.cancelAll()
            notificationManager = null

            coroutineScope.cancel()

        }.onFailure {
            Timber.e("Failed onDestroy in PlayerService ${it.stackTraceToString()}")
        }
        super.onDestroy()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            persistentQueueKey -> if (sharedPreferences != null) {
                isPersistentQueueEnabled =
                    sharedPreferences.getBoolean(key, isPersistentQueueEnabled)
            }

            volumeNormalizationKey, loudnessBaseGainKey -> maybeNormalizeVolume()

            resumePlaybackWhenDeviceConnectedKey -> maybeResumePlaybackWhenDeviceConnected()

            skipSilenceKey -> if (sharedPreferences != null) {
                player.skipSilenceEnabled = sharedPreferences.getBoolean(key, false)
            }

            queueLoopTypeKey -> {
                player.repeatMode =
                    sharedPreferences?.getEnum(queueLoopTypeKey, QueueLoopType.Default)?.type
                        ?: QueueLoopType.Default.type
            }
        }
    }

    private var pausedByZeroVolume = false
    override fun onAudioVolumeChanged(currentVolume: Int, maxVolume: Int) {
        if (preferences.getBoolean(isPauseOnVolumeZeroEnabledKey, false)) {
            if (player.isPlaying && currentVolume < 1) {
                binder.callPause {}
                pausedByZeroVolume = true
            } else if (pausedByZeroVolume && currentVolume >= 1) {
                binder.player.play()
                pausedByZeroVolume = false
            }
        }
    }

    override fun onAudioVolumeDirectionChanged(direction: Int) {
        /*
        if (direction == 0) {
            binder.player.seekToPreviousMediaItem()
        } else {
            binder.player.seekToNextMediaItem()
        }

         */
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {

        println("PlayerServiceModern onMediaItemTransition mediaItem $mediaItem reason $reason")

        currentMediaItem.update { mediaItem }
        maybeRecoverPlaybackError()
        maybeNormalizeVolume()

        loadFromRadio(reason)

        with(bitmapProvider) {
            var newUriForLoad = binder.player.currentMediaItem?.mediaMetadata?.artworkUri
            if(lastUri == binder.player.currentMediaItem?.mediaMetadata?.artworkUri) {
                newUriForLoad = null
            }

            load(newUriForLoad, {
                updateDefaultNotification()
                updateWidgets()
            })
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            maybeSavePlayerQueue()
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateDefaultNotification()
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] = shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    @UnstableApi
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val fadeDisabled = preferences.getEnum(
            playbackFadeAudioDurationKey,
            DurationInMilliseconds.Disabled
        ) == DurationInMilliseconds.Disabled
        val duration = preferences.getEnum(
            playbackFadeAudioDurationKey,
            DurationInMilliseconds.Disabled
        ).asMillis
        if (isPlaying && !fadeDisabled)
            startFadeAnimator(
                player = binder.player,
                duration = duration.toInt(),
                fadeIn = true
            )

        //val totalPlayTimeMs = player.totalBufferedDuration.toString()
        //Log.d("mediaEvent","isPlaying "+isPlaying.toString() + " buffered duration "+totalPlayTimeMs)
        //Log.d("mediaItem","onIsPlayingChanged isPlaying $isPlaying audioSession ${player.audioSessionId}")


        super.onIsPlayingChanged(isPlaying)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Timber.e("PlayerService onPlayerError ${error.stackTraceToString()}")
        println("mediaItem onPlayerError errorCode ${error.errorCode} errorCodeName ${error.errorCodeName}")
        if (error.errorCode in PlayerErrorsToReload) {
            //println("mediaItem onPlayerError recovered occurred errorCodeName ${error.errorCodeName}")
            player.pause()
            player.prepare()
            player.play()
            return
        }

        /*
        if (error.errorCode in PlayerErrorsToSkip) {
            //println("mediaItem onPlayerError recovered occurred 2000 errorCodeName ${error.errorCodeName}")
            player.pause()
            player.prepare()
            player.forceSeekToNext()
            player.play()

            showSmartMessage(
                message = getString(
                    R.string.skip_media_on_notavailable_message,
                ))

            return
        }
         */


        if (!preferences.getBoolean(skipMediaOnErrorKey, false) || !player.hasNextMediaItem())
            return

        val prev = player.currentMediaItem ?: return
        //player.seekToNextMediaItem()
        player.playNext()

        showSmartMessage(
            message = getString(
                R.string.skip_media_on_error_message,
                prev.mediaMetadata.title
            )
        )

    }

//    override fun onPlaybackStateChanged(playbackState: Int) {
//        if (playbackState == STATE_IDLE) {
//            player.shuffleModeEnabled = false
//            //player.clearMediaItems()
//        }
//    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
            }
        }

//        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
//            currentMediaItem.value = player.currentMediaItem
//        }
    }


    private fun maybeRecoverPlaybackError() {
        if (player.playerError != null) {
            player.prepare()
        }
    }

    private fun loadFromRadio(reason: Int) {
        if (!preferences.getBoolean(autoLoadSongsInQueueKey, true)) return
        /*
        // Old feature add songs only if radio is started by user and when last song in player is played
        radio?.let { radio ->
            if (player.mediaItemCount - player.currentMediaItemIndex == 1) {
                coroutineScope.launch(Dispatchers.Main) {
                    player.addMediaItems(radio.process())
                }
            }
        }

         */

        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 3
        ) {
            if (radio == null) {
                binder.setupRadio(
                    NavigationEndpoint.Endpoint.Watch(
                        videoId = player.currentMediaItem?.mediaId
                    )
                )
            } else {
                radio?.let { radio ->
                    //if (player.mediaItemCount - player.currentMediaItemIndex <= 3) {
                    coroutineScope.launch(Dispatchers.Main) {
                        if (player.playbackState != STATE_IDLE)
                            player.addMediaItems(radio.process())
                    }
                    //}
                }
            }
        }
    }

    @UnstableApi
    private fun maybeNormalizeVolume() {
        if (!preferences.getBoolean(volumeNormalizationKey, false)) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            player.volume = 1f
            return
        }

        runCatching {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            }
        }.onFailure {
            Timber.e("PlayerService maybeNormalizeVolume load loudnessEnhancer ${it.stackTraceToString()}")
            println("PlayerService maybeNormalizeVolume load loudnessEnhancer ${it.stackTraceToString()}")
            return
        }

        val baseGain = preferences.getFloat(loudnessBaseGainKey, 5.00f)
        player.currentMediaItem?.mediaId?.let { songId ->
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob = coroutineScope.launch(Dispatchers.Main) {
                fun Float?.toMb() = ((this ?: 0f) * 100).toInt()
                Database.loudnessDb(songId).cancellable().collectLatest { loudnessDb ->
                    val loudnessMb = loudnessDb.toMb().let {
                        if (it !in -2000..2000) {
                            withContext(Dispatchers.Main) {
                                SmartMessage(
                                    "Extreme loudness detected",
                                    context = this@PlayerServiceModern
                                )
                                /*
                                SmartMessage(
                                    getString(
                                        R.string.loudness_normalization_extreme,
                                        getString(R.string.format_db, (it / 100f).toString())
                                    )
                                )
                                 */
                            }

                            0
                        } else it
                    }
                    try {
                        //default
                        //loudnessEnhancer?.setTargetGain(-((loudnessDb ?: 0f) * 100).toInt() + 500)
                        loudnessEnhancer?.setTargetGain(baseGain.toMb() - loudnessMb)
                        loudnessEnhancer?.enabled = true
                    } catch (e: Exception) {
                        Timber.e("PlayerService maybeNormalizeVolume apply targetGain ${e.stackTraceToString()}")
                        println("PlayerService maybeNormalizeVolume apply targetGain ${e.stackTraceToString()}")
                    }
                }
            }
        }
    }


    @SuppressLint("NewApi")
    private fun maybeResumePlaybackWhenDeviceConnected() {
        if (!isAtLeastAndroid6) return

        if (preferences.getBoolean(resumePlaybackWhenDeviceConnectedKey, false)) {
            if (audioManager == null) {
                audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?
            }

            audioDeviceCallback = object : AudioDeviceCallback() {
                private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo): Boolean {
                    if (!audioDeviceInfo.isSink) return false

                    return audioDeviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }

                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    if (!player.isPlaying && addedDevices.any(::canPlayMusic)) {
                        player.play()
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = Unit
            }

            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, handler)

        } else {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
        }
    }

    private fun createRendersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            val minimumSilenceDuration = preferences.getLong(
                minimumSilenceDurationKey, 2_000_000L
            ).coerceIn(1000L..2_000_000L)

            return DefaultAudioSink.Builder(applicationContext)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioOffloadSupportProvider(
                    DefaultAudioOffloadSupportProvider(applicationContext)
                )
                .setAudioProcessorChain(
                    DefaultAudioProcessorChain(
                        arrayOf(),
                        SilenceSkippingAudioProcessor(
                            /* minimumSilenceDurationUs = */ minimumSilenceDuration,
                            /* silenceRetentionRatio = */ 0.01f,
                            /* maxSilenceToKeepDurationUs = */ minimumSilenceDuration,
                            /* minVolumeToKeepPercentageWhenMuting = */ 0,
                            /* silenceThresholdLevel = */ 256
                        ),
                        SonicAudioProcessor()
                    )
                )
                .build()
                .apply {
                    if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
                }
        }
    }

    private fun createMediaSourceFactory() = DefaultMediaSourceFactory(
        createDataSourceFactory(),
        DefaultExtractorsFactory()
    ).setLoadErrorHandlingPolicy(
        object : DefaultLoadErrorHandlingPolicy() {
            override fun isEligibleForFallback(exception: IOException) = true
        }
    )


    private fun buildCustomCommandButtons(): MutableList<CommandButton> {
        val notificationPlayerFirstIcon = preferences.getEnum(notificationPlayerFirstIconKey, NotificationButtons.Download)
        val notificationPlayerSecondIcon = preferences.getEnum(notificationPlayerSecondIconKey, NotificationButtons.Favorites)

        val commandButtonsList = mutableListOf<CommandButton>()
        val firstCommandButton = NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerFirstIcon }
                .map {
                    val displayName = appContext().resources.getString( it.textId )

                    CommandButton.Builder()
                        .setDisplayName( displayName )
                        .setIconResId(
                            it.getStateIcon(
                                it,
                                currentSong.value?.likedAt,
                                currentSongStateDownload.value,
                                player.repeatMode,
                                player.shuffleModeEnabled
                            )
                        )
                        .setSessionCommand(it.sessionCommand)
                        .build()
                }
        }

        val secondCommandButton =  NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerSecondIcon }
                .map {
                    val displayName = appContext().resources.getString( it.textId )

                    CommandButton.Builder()
                        .setDisplayName( displayName )
                        .setIconResId(
                            it.getStateIcon(
                                it,
                                currentSong.value?.likedAt,
                                currentSongStateDownload.value,
                                player.repeatMode,
                                player.shuffleModeEnabled
                            )
                        )
                        .setSessionCommand(it.sessionCommand)
                        .build()
                }
        }

        val otherCommandButtons = NotificationButtons.entries.let { buttons ->
            buttons
                .filterNot { it == notificationPlayerFirstIcon || it == notificationPlayerSecondIcon }
                .map {
                    val displayName = appContext().resources.getString( it.textId )

                    CommandButton.Builder()
                        .setDisplayName( displayName )
                        .setIconResId(
                            it.getStateIcon(
                                it,
                                currentSong.value?.likedAt,
                                currentSongStateDownload.value,
                                player.repeatMode,
                                player.shuffleModeEnabled
                            )
                        )
                        .setSessionCommand(it.sessionCommand)
                        .build()
                }
        }

        commandButtonsList += firstCommandButton + secondCommandButton + otherCommandButtons

        return commandButtonsList
    }

    private fun updateCustomNotification(session: MediaSession): MediaNotification {

        val playIntent = Action.play.pendingIntent
        val pauseIntent = Action.pause.pendingIntent
        val nextIntent = Action.next.pendingIntent
        val prevIntent = Action.previous.pendingIntent

        val mediaMetadata = player.mediaMetadata

        bitmapProvider.load(mediaMetadata.artworkUri) {}

        val customNotify = if (isAtLeastAndroid8) {
            NotificationCompat.Builder(this, NotificationChannelId)
        } else {
            NotificationCompat.Builder(this)
        }
            .setContentTitle(cleanPrefix(player.mediaMetadata.title.toString()))
            .setContentText(
                if (mediaMetadata.albumTitle != null && mediaMetadata.artist != "")
                    "${mediaMetadata.artist} | ${mediaMetadata.albumTitle}"
                else mediaMetadata.artist
            )
            .setSubText(
                if (mediaMetadata.albumTitle != null && mediaMetadata.artist != "")
                    "${mediaMetadata.artist} | ${mediaMetadata.albumTitle}"
                else mediaMetadata.artist
            )
            .setLargeIcon(bitmapProvider.bitmap)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSmallIcon(player.playerError?.let { R.drawable.alert_circle }
                ?: R.drawable.app_icon)
            .setOngoing(false)
            .setContentIntent(activityPendingIntent<MainActivity>(
                flags = PendingIntent.FLAG_UPDATE_CURRENT
            ) {
                putExtra("expandPlayerBottomSheet", true)
            })
            .setDeleteIntent(broadCastPendingIntent<NotificationDismissReceiver>())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .addAction(R.drawable.play_skip_back, "Skip back", prevIntent)
            .addAction(
                if (player.isPlaying) R.drawable.pause else R.drawable.play,
                if (player.isPlaying) "Pause" else "Play",
                if (player.isPlaying) pauseIntent else playIntent
            )
            .addAction(R.drawable.play_skip_forward, "Skip forward", nextIntent)

        //***********************
        val notificationPlayerFirstIcon = preferences.getEnum(notificationPlayerFirstIconKey, NotificationButtons.Download)
        val notificationPlayerSecondIcon = preferences.getEnum(notificationPlayerSecondIconKey, NotificationButtons.Favorites)

        NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerFirstIcon }
                .map {
                    customNotify.addAction(
                        it.getStateIcon(
                            it,
                            currentSong.value?.likedAt,
                            currentSongStateDownload.value,
                            player.repeatMode,
                            player.shuffleModeEnabled
                        ),
                        appContext().resources.getString( it.textId ),
                        it.pendingIntent
                    )
                }
        }

        NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerSecondIcon }
                .map {
                    customNotify.addAction(
                        it.getStateIcon(
                            it,
                            currentSong.value?.likedAt,
                            currentSongStateDownload.value,
                            player.repeatMode,
                            player.shuffleModeEnabled
                        ),
                        appContext().resources.getString( it.textId ),
                        it.pendingIntent
                    )
                }
        }

        NotificationButtons.entries.let { buttons ->
            buttons
                .filterNot { it == notificationPlayerFirstIcon || it == notificationPlayerSecondIcon }
                .map {
                    customNotify.addAction(
                        it.getStateIcon(
                            it,
                            currentSong.value?.likedAt,
                            currentSongStateDownload.value,
                            player.repeatMode,
                            player.shuffleModeEnabled
                        ),
                        appContext().resources.getString( it.textId ),
                        it.pendingIntent
                    )
                }
        }
        //***********************

        updateWallpaper()

        return MediaNotification(NotificationId, customNotify.build())
    }

    private fun updateWallpaper() {
        val wallpaperEnabled = preferences.getBoolean(enableWallpaperKey, false)
        val wallpaperType = preferences.getEnum(wallpaperTypeKey, WallpaperType.Lockscreen)
        if (isAtLeastAndroid7 && wallpaperEnabled) {
            coroutineScope.launch(Dispatchers.IO) {
                val wpManager = WallpaperManager.getInstance(this@PlayerServiceModern)
                wpManager.setBitmap(bitmapProvider.bitmap, null, true,
                    when (wallpaperType) {
                        WallpaperType.Both -> (FLAG_LOCK or FLAG_SYSTEM)
                        WallpaperType.Lockscreen -> FLAG_LOCK
                        WallpaperType.Home -> FLAG_SYSTEM
                    }
                )
            }
        }
    }

    private fun updateDefaultNotification() {
        coroutineScope.launch(Dispatchers.Main) {
            mediaSession.setCustomLayout( buildCustomCommandButtons() )
        }

    }


    private fun updateDiscordPresence() {
        val isDiscordPresenceEnabled = preferences.getBoolean(isDiscordPresenceEnabledKey, false)
        if (!isDiscordPresenceEnabled || !isAtLeastAndroid81) return

        val discordPersonalAccessToken = encryptedPreferences.getString(
            discordPersonalAccessTokenKey, ""
        )

        runCatching {
            if (!discordPersonalAccessToken.isNullOrEmpty()) {
                player.currentMediaItem?.let {
                    sendDiscordPresence(
                        discordPersonalAccessToken,
                        it,
                        timeStart = if (player.isPlaying)
                            System.currentTimeMillis() - player.currentPosition else 0L,
                        timeEnd = if (player.isPlaying)
                            (System.currentTimeMillis() - player.currentPosition) + player.duration else 0L
                    )
                }
            }
        }.onFailure {
            Timber.e("PlayerService Failed sendDiscordPresence in PlayerService ${it.stackTraceToString()}")
        }
    }


    fun toggleLike() {
        binder.toggleLike()
    }

    fun toggleDownload() {
        binder.toggleDownload()
    }

    fun toggleRepeat() {
        binder.toggleRepeat()
    }

    fun toggleShuffle() {
        binder.toggleShuffle()
    }

    fun startRadio() {
       binder.startRadio()
    }

    fun callActionPause() {
        binder.callPause({})
    }

    private fun showSmartMessage(message: String) {
        coroutineScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.Main) {
                SmartMessage(
                    message,
                    type = PopupType.Info,
                    durationLong = true,
                    context = this@PlayerServiceModern
                )
            }
        }
    }

    fun updateWidgets() {

        val songTitle = player.mediaMetadata.title.toString()
        val songArtist = player.mediaMetadata.artist.toString()
        val isPlaying = player.isPlaying
        coroutineScope.launch {
            playerVerticalWidget.updateInfo(
                context = applicationContext,
                songTitle = songTitle,
                songArtist = songArtist,
                isPlaying = isPlaying,
                bitmap = bitmapProvider.bitmap,
                player = player
            )
            playerHorizontalWidget.updateInfo(
                context = applicationContext,
                songTitle = songTitle,
                songArtist = songArtist,
                isPlaying = isPlaying,
                bitmap = bitmapProvider.bitmap,
                player = player
            )
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

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        Timber.d("PlayerServiceModern onPositionDiscontinuity oldPosition ${oldPosition.mediaItemIndex} newPosition ${newPosition.mediaItemIndex} reason $reason")
        println("PlayerServiceModern onPositionDiscontinuity oldPosition ${oldPosition.mediaItemIndex} newPosition ${newPosition.mediaItemIndex} reason $reason")
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    private fun maybeSavePlayerQueue() {
        println("PlayerServiceModern onCreate savePersistentQueue")
        if (!isPersistentQueueEnabled) return
        println("PlayerServiceModern onCreate savePersistentQueue is enabled")

        CoroutineScope(Dispatchers.Main).launch {
            val mediaItems = player.currentTimeline.mediaItems
            val mediaItemIndex = player.currentMediaItemIndex
            val mediaItemPosition = player.currentPosition

            if (mediaItems.isEmpty()) return@launch


            mediaItems.mapIndexed { index, mediaItem ->
                QueuedMediaItem(
                    mediaItem = mediaItem,
                    position = if (index == mediaItemIndex) mediaItemPosition else null
                )
            }.let { queuedMediaItems ->
                if (queuedMediaItems.isEmpty()) return@let

                Database.asyncTransaction {
                    clearQueue()
                    insert( queuedMediaItems )
                }

                Timber.d("PlayerServiceModern QueuePersistentEnabled Saved queue")
            }

        }
    }

    private fun maybeResumePlaybackOnStart() {
        if(!isPersistentQueueEnabled || !preferences.getBoolean(resumePlaybackOnStartKey, false)) return

        if(!player.isPlaying) {
            player.play()
        }
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestorePlayerQueue() {
        if (!isPersistentQueueEnabled) return

        Database.asyncQuery {
            val queuedSong = queue()

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

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestoreFromDiskPlayerQueue() {
        //if (!isPersistentQueueEnabled) return
        //Log.d("mediaItem", "QueuePersistentEnabled Restore Initial")

        runCatching {
            filesDir.resolve("persistentQueue.data").inputStream().use { fis ->
                ObjectInputStream(fis).use { oos ->
                    oos.readObject() as PersistentQueue
                }
            }
        }.onSuccess { queue ->
            //Log.d("mediaItem", "QueuePersistentEnabled Restored queue $queue")
            //Log.d("mediaItem", "QueuePersistentEnabled Restored ${queue.songMediaItems.size}")
            runBlocking(Dispatchers.Main) {
                player.setMediaItems(
                    queue.songMediaItems.map { song ->
                        song.asMediaItem.buildUpon()
                            .setUri(song.asMediaItem.mediaId)
                            .setCustomCacheKey(song.asMediaItem.mediaId)
                            .build().apply {
                                mediaMetadata.extras?.putBoolean("isFromPersistentQueue", true)
                            }
                    },
                    queue.mediaItemIndex,
                    queue.position
                )

                player.prepare()

            }

        }.onFailure {
            //it.printStackTrace()
            Timber.e(it.stackTraceToString())
        }

        //Log.d("mediaItem", "QueuePersistentEnabled Restored ${player.currentTimeline.mediaItems.size}")

    }

    private fun maybeSaveToDiskPlayerQueue() {

        //if (!isPersistentQueueEnabled) return
        //Log.d("mediaItem", "QueuePersistentEnabled Save ${player.currentTimeline.mediaItems.size}")

        val persistentQueue = PersistentQueue(
            title = "title",
            songMediaItems = player.currentTimeline.mediaItems.map {
                PersistentSong(
                    id = it.mediaId,
                    title = it.mediaMetadata.title.toString(),
                    durationText = it.mediaMetadata.extras?.getString("durationText").toString(),
                    thumbnailUrl = it.mediaMetadata.artworkUri.toString()
                )
            },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )

        runCatching {
            filesDir.resolve("persistentQueue.data").outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistentQueue)
                }
            }
        }.onFailure {
            //it.printStackTrace()
            Timber.e(it.stackTraceToString())

        }.onSuccess {
            Log.d("mediaItem", "QueuePersistentEnabled Saved $persistentQueue")
        }

    }

    fun updateDownloadedState() {
        if (currentSong.value == null) return
        val mediaId = currentSong.value!!.id
        val downloads = MyDownloadHelper.downloads.value
        currentSongStateDownload.value = downloads[mediaId]?.state ?: Download.STATE_STOPPED
        /*
        if (downloads[currentSong.value?.id]?.state == Download.STATE_COMPLETED) {
            currentSongIsDownloaded.value = true
        } else {
            currentSongIsDownloaded.value = false
        }
        */
        println("PlayerServiceModern updateDownloadedState downloads count ${downloads.size} currentSongIsDownloaded ${currentSong.value?.id}")
        updateDefaultNotification()

    }

    /**
     * This method should ONLY be called when the application (sc. activity) is in the foreground!
     */
    fun restartForegroundOrStop() {
        binder.restartForegroundOrStop()
    }

    @UnstableApi
    class CustomMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
            val customMetadata = MediaMetadata.Builder()
                .setTitle(cleanPrefix(metadata.title?.toString() ?: ""))
                .build()
            return super.getNotificationContentTitle(customMetadata)
        }

//        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
//            val customMetadata = MediaMetadata.Builder()
//                .setArtist(cleanPrefix(metadata.artist?.toString() ?: ""))
//                .build()
//            return super.getNotificationContentText(customMetadata)
//        }
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
            when (intent.action) {
                Action.pause.value -> binder.callPause({ player.pause() } )
                Action.play.value -> player.play()
                Action.next.value -> player.playNext()
                Action.previous.value -> player.playPrevious()
                Action.like.value -> {
                    binder.toggleLike()
                }

                Action.download.value -> {
                    binder.toggleDownload()
                }

                Action.playradio.value -> {
                    binder.stopRadio()
                    binder.playRadio(NavigationEndpoint.Endpoint.Watch(videoId = binder.player.currentMediaItem?.mediaId))
                }

                Action.shuffle.value -> {
                    binder.toggleShuffle()
                }

                Action.search.value -> {
                    binder.actionSearch()
                }

                Action.repeat.value -> {
                    binder.toggleRepeat()
                }


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
            get() = this@PlayerServiceModern.downloadCache

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
                    .setSmallIcon(R.drawable.app_icon)
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


        @UnstableApi
        private fun startRadio(endpoint: NavigationEndpoint.Endpoint.Watch?, justAdd: Boolean, filterArtist: String = "") {
            radioJob?.cancel()
            radio = null
            val isDiscoverEnabled = applicationContext.preferences.getBoolean(discoverKey, false)
            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params,
                isDiscoverEnabled,
                applicationContext
            ).let {
                isLoadingRadio = true
                radioJob = coroutineScope.launch(Dispatchers.Main) {

                    val songs = if (filterArtist.isEmpty()) it.process()
                    else it.process().filter { song -> song.mediaMetadata.artist == filterArtist }

                    songs.forEach {
                        Database.asyncTransaction { insert(it) }
                    }

                    if (justAdd) {
                        player.addMediaItems(songs.drop(1))
                    } else {
                        player.forcePlayFromBeginning(songs)
                    }
                    radio = it
                    isLoadingRadio = false
                }
            }
        }

        fun stopRadio() {
            isLoadingRadio = false
            radioJob?.cancel()
            radio = null
        }

        fun playFromSearch(query: String) {
            coroutineScope.launch {
                Innertube.searchPage(
                    body = SearchBody(
                        query = query,
                        params = Innertube.SearchFilter.Song.value
                    ),
                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                )?.getOrNull()?.items?.firstOrNull()?.info?.endpoint?.let { playRadio(it) }
            }
        }

        @UnstableApi
        fun setupRadio(endpoint: NavigationEndpoint.Endpoint.Watch?, filterArtist: String = "") =
            startRadio(endpoint = endpoint, justAdd = true, filterArtist = filterArtist)

        @UnstableApi
        fun playRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = false)

        fun callPause(onPause: () -> Unit) {
            val fadeDisabled = preferences.getEnum(
                playbackFadeAudioDurationKey,
                DurationInMilliseconds.Disabled
            ) == DurationInMilliseconds.Disabled
            val duration = preferences.getEnum(
                playbackFadeAudioDurationKey,
                DurationInMilliseconds.Disabled
            ).asMillis
            if (player.isPlaying) {
                if (fadeDisabled) {
                    player.pause()
                    onPause()
                } else {
                    //fadeOut
                    startFadeAnimator(player, duration.toInt(), false) {
                        player.pause()
                        onPause()
                    }
                }
            }
        }

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
                    like(
                        it.id,
                        setLikeState(it.likedAt)
                    )
                }.also {
                    currentSong.debounce(1000).collect(coroutineScope) { updateDefaultNotification() }
                }
            }

            currentSong.value
                ?.let { MyDownloadHelper.autoDownloadWhenLiked(this@PlayerServiceModern, it.asMediaItem) }
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
            updateDefaultNotification()
        }

        fun toggleShuffle() {
            player.toggleShuffleMode()
            updateDefaultNotification()
        }

        fun startRadio() {
            binder.stopRadio()
            binder.playRadio(NavigationEndpoint.Endpoint.Watch(videoId = binder.player.currentMediaItem?.mediaId))
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

    }

}