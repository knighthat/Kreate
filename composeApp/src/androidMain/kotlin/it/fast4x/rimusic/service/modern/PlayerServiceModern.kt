package it.fast4x.rimusic.service.modern

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.playback.AudioHandler
import app.kreate.android.service.player.LiveWallpaperEngine
import app.kreate.android.service.player.PlaybackController
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.player.VolumeObserver
import app.kreate.android.utils.isLocalFile
import app.kreate.di.CacheType
import co.touchlab.kermit.Logger
import com.google.common.util.concurrent.MoreExecutors
import io.ktor.client.HttpClient
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.utils.CoilBitmapLoader
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.discord.Discord
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


val MediaItem.isLocal get() = localConfiguration?.uri?.isLocalFile() ?: false


@androidx.annotation.OptIn(UnstableApi::class)
class PlayerServiceModern:
    MediaLibraryService(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    KoinComponent
{
    private val cache: Cache by inject(CacheType.CACHE)
    private val discord: Discord by inject()
    private val player: StatefulPlayer by inject()
    private val downloadHelper: DownloadHelper by inject()
    private val volumeObserver: VolumeObserver by inject()
    private val logger = Logger.withTag( this::class.java.simpleName )

    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private val handler = Handler(Looper.getMainLooper())
    private val downloadListener = DownloadStateListener()
    private lateinit var mediaSession: MediaLibrarySession
    private var mediaLibrarySessionCallback: MediaLibrarySessionCallback =
        MediaLibrarySessionCallback(this)
    private lateinit var audioHandler: AudioHandler

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

    private fun observeLikeState() {
        coroutineScope.launch(Dispatchers.IO) {
            player.currentMediaItemState.filterNotNull().collect {
                Database.songTable
                        .isLiked( it.mediaId )
                        .collect { updateMediaControl() }
            }
        }
    }

    private fun downloadCurrentMediaItem() {
        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val isDownloaded = MyDownloadHelper.instance.downloads.value[mediaId]?.state == Download.STATE_COMPLETED
        if( !isDownloaded ) {
            logger.v { "Downloading current media item ($mediaId)" }

            manageDownload( this, mediaItem, false )
        } else
            Toaster.i( R.string.info_song_already_downloaded )
    }

    /**
     * (Re)render media control in notification area.
     */
    private fun updateMediaControl() {
        coroutineScope.launch( Dispatchers.Default ) {
            val mutButtons = mutableListOf<CommandButton>()

            val firstButton by Preferences.MEDIA_NOTIFICATION_FIRST_ICON
            PlaybackController.makeButton(this@PlayerServiceModern, player, firstButton)
                              .also( mutButtons::add )
            val secondButton by Preferences.MEDIA_NOTIFICATION_SECOND_ICON
            PlaybackController.makeButton(this@PlayerServiceModern, player, secondButton)
                              .also( mutButtons::add )
            NotificationButtons.entries
                .filterNot { it === firstButton || it === secondButton }
                .map { PlaybackController.makeButton(this@PlayerServiceModern, player, it) }
                .also( mutButtons::addAll )

            val buttons = mutButtons.toList()
            withContext( Dispatchers.Main ) {
                mediaSession.setMediaButtonPreferences( buttons )
            }
        }
    }

    override fun onStartCommand( intent: Intent?, flags: Int, startId: Int ): Int {
        logger.v { "Received command ${intent?.action}" }

        when( intent?.action ) {
            ACTION_RESTART -> {
                player.pause()
                stopSelf()
            }
            ACTION_LIKE -> {
                val mediaItem = player.currentMediaItem
                Database.asyncTransaction {
                    mediaItem ?: return@asyncTransaction

                    songTable.toggleLike( mediaItem.mediaId )
                    MyDownloadHelper.autoDownloadWhenLiked( mediaItem )
                }
            }
            ACTION_DOWNLOAD -> downloadCurrentMediaItem()
            ACTION_UPDATE_MEDIA_CONTROL -> updateMediaControl()

            PLAYER_ACTION_PLAY -> player.play()
            PLAYER_ACTION_PAUSE -> player.pause()
            PLAYER_ACTION_NEXT -> player.seekToNext()
            PLAYER_ACTION_PREVIOUS -> player.seekToPrevious()
            PLAYER_ACTION_CYCLE_REPEAT -> player.cycleRepeatMode()
            PLAYER_ACTION_TOGGLE_SHUFFLE -> player.toggleShuffleMode()
            PLAYER_ACTION_TOGGLE_RADIO -> Preferences.PLAYER_ACTION_START_RADIO.flip()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        Innertube.client = inject<HttpClient>().value

        super.onCreate()

        audioHandler = AudioHandler(this, handler, player)
        volumeObserver.register()

        DefaultMediaNotificationProvider(this)
            .apply { setSmallIcon( R.drawable.app_icon_monochrome ) }
            .also( ::setMediaNotificationProvider )

        MyDownloadHelper.instance = this.downloadHelper

        preferences.registerOnSharedPreferenceChangeListener(this)
        preferences.registerOnSharedPreferenceChangeListener(audioHandler)

        // Build the media library session
        mediaSession = MediaLibrarySession
            .Builder(this, player.toForwardingPlayer(), mediaLibrarySessionCallback)
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

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, PlayerServiceModern::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        MyDownloadHelper.instance.downloadManager.addListener(downloadListener)

        //<editor-fold desc="Preferences">
        if( Preferences.isLoggedInToDiscord() ) {
            val token by Preferences.DISCORD_ACCESS_TOKEN
            discord.login( token )
        }
        if( Preferences.LIVE_WALLPAPER.value > 0 )
            registerLiveWallpaperEngine()
        //</editor-fold>
        //<editor-fold desc="Low priority tasks">
        observeLikeState()
        //</editor-fold>
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    @UnstableApi
    override fun onDestroy() {
        runCatching {
            volumeObserver.unregister()
            runBlocking( Dispatchers.Default ) {
                unregisterLiveWallpaperEngine()
            }
            MyDownloadHelper.instance.downloadManager.removeListener( downloadListener )

            stopService(intent<MyDownloadService>())
            stopService(intent<PlayerServiceModern>())

            player.stop()
            player.release()

            audioHandler.unregister()
            mediaSession.release()
            cache.release()
            //downloadCache.release()
            MyDownloadHelper.instance.downloadManager.removeListener(downloadListener)

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

            Preferences.Key.PLAYER_ACTION_START_RADIO -> updateMediaControl()
        }
    }

    companion object {
        const val SleepTimerNotificationId = 1002
        const val SleepTimerNotificationChannelId = "sleep_timer_channel_id"

        val PlayerErrorsToReload = arrayOf(416, 4003)
        val PlayerErrorsToSkip = arrayOf(2000)

        const val ACTION_RESTART = "restart"
        const val ACTION_DOWNLOAD = "DOWNLOAD"
        const val ACTION_LIKE = "LIKE"
        const val ACTION_UPDATE_MEDIA_CONTROL = "UPDATE_MEDIA_CONTROL"
        //<editor-fold desc="Player actions">
        const val PLAYER_ACTION_PLAY = "PLAYER_PLAY"
        const val PLAYER_ACTION_PAUSE = "PLAYER_PAUSE"
        const val PLAYER_ACTION_NEXT = "PLAYER_NEXT"
        const val PLAYER_ACTION_PREVIOUS = "PLAYER_PREVIOUS"
        const val PLAYER_ACTION_CYCLE_REPEAT = "PLAYER_CYCLE_REPEAT"
        const val PLAYER_ACTION_TOGGLE_SHUFFLE = "PLAYER_TOGGLE_SHUFFLE"
        const val PLAYER_ACTION_TOGGLE_RADIO = "PLAYER_TOGGLE_RADIO"
        //</editor-fold>
    }

    private inner class DownloadStateListener : DownloadManager.Listener {

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: java.lang.Exception?
        ) {
            val reqId = download.request.id
            val currId = player.currentMediaItem?.mediaId

            if( currId == reqId )
                updateMediaControl()
        }

        override fun onDownloadRemoved( downloadManager: DownloadManager, download: Download ) =
            onDownloadChanged( downloadManager, download, null )
    }
}