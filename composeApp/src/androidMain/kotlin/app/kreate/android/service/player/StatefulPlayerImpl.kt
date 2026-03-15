package app.kreate.android.service.player

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.PlayerEventUpdateDiscord
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.database.models.Song
import co.touchlab.kermit.Logger
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.PlayerServiceModern.Companion.SleepTimerNotificationId
import it.fast4x.rimusic.utils.TimerJob
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration


/**
 * A custom ExoPlayer with additional features:
 * - Fading effect
 * - Observable states (current mediaItem, timeline, window, etc.)
 */
@OptIn(UnstableApi::class)
class StatefulPlayerImpl(
    private val player: ExoPlayer
): ExoPlayer by player, StatefulPlayer, Player.Listener, KoinComponent {

    companion object {
        const val NotificationId = 1001
        const val SleepTimerNotificationChannelId = "sleep_timer_channel_id"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = Logger.withTag("StatefulPlayer")
    private val _currentMediaItemState = MutableStateFlow<MediaItem?>(null)
    private val _currentTimelineState = MutableStateFlow(Timeline.EMPTY)
    private val _currentWindowState = MutableStateFlow<Timeline.Window?>(null)

    private var volumeAnimator: ValueAnimator? = null
    private var radioJob: Job? = null
    private var timerJob: TimerJob? = null

    override val currentMediaItemState = _currentMediaItemState.asStateFlow()
    override val currentTimelineState = _currentTimelineState.asStateFlow()
    override val currentWindowState = _currentWindowState.asStateFlow()

    init {
        this.addListener( this )
        this.addListener( PlayerEventUpdateDiscord() )
    }

    private fun stopFadingEffect() {
        volumeAnimator?.cancel()
        volumeAnimator = null
    }

    /**
     * Calculates the perceptually scaled volume based on a linear progress.
     * This uses a logarithmic curve to make the fade sound more natural.
     *
     * @param linearProgress A linear value from 0.0f to 1.0f representing the fade progress.
     * @param startVolume The starting volume for the current fade.
     * @param targetVolume The target volume for the current fade.
     * @return The volume value (0.0f to 1.0f) to set on ExoPlayer.
     */
    private fun getVolumeForProgress(
        linearProgress: Float,
        startVolume: Float,
        targetVolume: Float
    ): Float {
        // Adjust this factor to change the steepness of the curve.
        // A common range is 2.0 to 4.0. Higher values make the lower end steeper.
        val curveFactor = 3.0f

        // Apply a power curve (logarithmic perception)
        // This formula maps a linear input (linearProgress) to a more perceptually linear output.
        val scaledProgress = if (linearProgress <= 0f) {
            0f
        } else if (linearProgress >= 1f) {
            1f
        } else {
            (10f.pow(curveFactor * linearProgress) - 1f) / (10f.pow(curveFactor) - 1f)
        }

        // Interpolate between start and target volume using the scaled progress
        return startVolume + (targetVolume - startVolume) * scaledProgress
    }

    /**
     * Starts a volume fade from a start volume to a target volume over a specified duration.
     * The fade uses a logarithmic curve for perceptual smoothness.
     *
     * @param start The volume to start the fade from (0.0f to 1.0f).
     * @param end The volume to fade to (0.0f to 1.0f).
     */
    @MainThread
    private fun startFade(
        start: Float,
        end: Float,
        durationInMillis: Long,
        doOnStart: (Animator) -> Unit = {},
        doOnEnd: (Animator) -> Unit = {}
    ) {
        stopFadingEffect()

        if( durationInMillis == 0L )
            return

        with( ValueAnimator.ofFloat( 0f, 1f ) ) {
            volumeAnimator = this

            duration = durationInMillis
            addUpdateListener { animator ->
                val floor = min(start, end)
                val ceil = max(start, end)

                player.volume = getVolumeForProgress(
                    linearProgress = animator.animatedValue as Float,
                    startVolume = start,
                    targetVolume = end
                ).coerceIn( floor, ceil )
            }
            doOnStart( doOnStart )
            doOnEnd( doOnEnd )

            start()
        }
    }

    /*
            StatefulPlayer
     */

    override fun isLoadingRadio(): Boolean = radioJob?.isActive == true

    override fun startRadio() { currentMediaItem?.let( ::startRadio ) }

    override fun startRadio(
        mediaItem: MediaItem,
        append: Boolean,
        endpoint: NavigationEndpoint.Endpoint.Watch?
    ) {
        this.stopRadio()

        // Play song immediately while other songs are being loaded
        if( player.currentMediaItem?.mediaId != mediaItem.mediaId )
            player.forcePlay( mediaItem )

        // Prevent UI from freezing up while data is being fetched
        radioJob = coroutineScope.launch {
            Innertube.radio(
                mediaItem.mediaId,
                CURRENT_LOCALE,
                endpoint?.playlistId ?: "RDAMVM${mediaItem.mediaId}",
                endpoint?.params
            ).onSuccess { relatedSongs ->
                // Launch another coroutine to make it run
                // in parallel with the rest of of block.
                launch( Dispatchers.IO ) {
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
            }.onFailure { err ->
                logger.e( "", err )
                Toaster.e( R.string.error_song_radio_failed )
            }
        }
    }

    override fun startRadio(
        song: Song,
        append: Boolean,
        endpoint: NavigationEndpoint.Endpoint.Watch?
    ) = startRadio( song.asMediaItem, append, endpoint )

    override fun stopRadio() {
        radioJob?.cancel()
        radioJob = null
    }

    override fun cycleRepeatMode() {
        repeatMode = when( repeatMode ) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_OFF
            // "else" shouldn't be executed at all,
            // if app crashes here, something went wrong, really wrong.
            else -> throw IllegalStateException()
        }

        if( repeatMode != REPEAT_MODE_OFF )
            shuffleModeEnabled = false
    }

    override fun toggleShuffleMode() {
        shuffleModeEnabled = !shuffleModeEnabled

        if( shuffleModeEnabled )
            repeatMode = REPEAT_MODE_OFF
    }

    override fun downloadCurrentMediaItem() {

        val mediaItem = currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val isDownloaded = MyDownloadHelper.instance.downloads.value[mediaId]?.state == Download.STATE_COMPLETED
        if( !isDownloaded ) {
            logger.v { "Downloading current media item ($mediaId)" }

            val context: Context by inject(Context::class.java)
            manageDownload( context, mediaItem, false )
        } else
            Toaster.i( R.string.info_song_already_downlaoded )
    }

    override fun startSleepTimer( duration: Duration ) {
        val context: Context by inject()
        val title = context.getString( R.string.sleep_timer_ended )
        timerJob = coroutineScope.timer( duration.inWholeMilliseconds ) {
            pause()

            val notification = NotificationCompat
                .Builder(context, SleepTimerNotificationChannelId)
                .setContentTitle(title)
                .setAutoCancel( true )
                .setOnlyAlertOnce( true )
                .setShowWhen( true )
                .setSmallIcon( R.drawable.time )
                .build()
            val manager = context.getSystemService<NotificationManager>()
            manager?.notify( SleepTimerNotificationId, notification )
        }
    }

    override fun stopSleepTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun sleepTimerRemaining(): Flow<Long?> = timerJob?.millisLeft ?: flowOf( null )

    /*
            ExoPlayer
     */

    override fun getSecondaryRenderer( index: Int ) = player.getSecondaryRenderer( index )

    override fun release() {
        stopFadingEffect()
        player.removeListener( this )
        player.release()
    }

    override fun play() {
        fun action() {
            if( playbackState == Player.STATE_IDLE )
                prepare()
            player.play()

            onIsPlayingChanged( true )
        }

        val duration = Preferences.AUDIO_FADE_DURATION.value.asMillis
        if( duration == 0L ) {
            action()
            return
        }

        startFade(
            start = 0f,
            end = volume,
            durationInMillis = duration,
            doOnStart = {
                volume = 0f
                action()
            }
        )
    }

    override fun pause() {
        val duration = Preferences.AUDIO_FADE_DURATION.value.asMillis
        if( duration == 0L ) {
            player.pause()
            return
        }

        val originalVolume = volume
        startFade(
            start = volume,
            end = 0f,
            durationInMillis = duration,
            doOnEnd = {
                player.pause()
                volume = originalVolume
            }
        )
    }

    override fun getBufferedPercentage(): Int =
        try {
            player.bufferedPercentage
        } catch ( e: IllegalArgumentException ) {
            Logger.e( "", e, this::class.java.simpleName )
            0
        }

    override fun getAudioSessionId(): Int = player.audioSessionId

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        // Don't update [_currentMediaItemState] when on repeat
        if( reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT )
            return

        _currentMediaItemState.update { mediaItem }
        _currentWindowState.update {
            mediaItem?.let {
                _currentTimelineState.value.getWindow( currentMediaItemIndex, Timeline.Window() )
            }
        }
    }

    override fun onTimelineChanged( timeline: Timeline, reason: Int ) =
        _currentTimelineState.update { timeline }

    override fun stop() {
        stopRadio()
        stopSleepTimer()
        stopFadingEffect()
        player.stop()
    }
}