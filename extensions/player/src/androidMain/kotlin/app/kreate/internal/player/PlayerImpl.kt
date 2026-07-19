package app.kreate.internal.player

import android.animation.Animator
import android.animation.ValueAnimator
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.player.MediaItem
import app.kreate.player.Player
import app.kreate.player.PlayerListener
import app.kreate.player.RepeatMode
import app.kreate.preferences.Preferences
import app.kreate.utils.innertube.toMediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import androidx.media3.common.Player as MediaPlayer


@Suppress("DEPRECATION")
@kotlin.OptIn(ExperimentalAtomicApi::class)
@OptIn(UnstableApi::class)
internal class PlayerImpl(
    private val actualPlayer: ExoPlayer
) : Player,
    ExoPlayer by actualPlayer,
    KoinComponent,
    PlayerListener
{

    private val logger = Logger.withTag( "RealPlayer" )
    private val _currentMediaItemState = MutableStateFlow<MediaItem?>(null)
    private val _currentRepeatModeState = MutableStateFlow(convertRepeatMode(repeatMode))
    private val _currentShuffleModeState = MutableStateFlow(shuffleModeEnabled)
    private val _queueState = MutableStateFlow(emptyList<MediaItem>())
    private val radioJob = AtomicReference<Job?>(null)
    private val scope by inject<CoroutineScope>()
    private val youtube get() = get<YouTube>()

    private var volumeAnimator: ValueAnimator? = null

    init {
        addListener( this )
    }

    /**
     * Finds the index of a specific [MediaItem] in the current playlist by its unique [mediaId].
     *
     * @param mediaId The unique string identifier of the media item to search for.
     * @return index of the media item within the playlist timeline, or `-1` if the item is not present.
     */
    private fun getMediaItemIndex( mediaId: String ): Int {
        val reusableWindow = Timeline.Window()
        for( i in 0 until currentTimeline.windowCount ) {
            // This mutates the existing object instead of creating a new one
            currentTimeline.getWindow( i, reusableWindow )
            if( reusableWindow.mediaItem.mediaId == mediaId )
                return i
        }

        return -1
    }

    /**
     * @throws IndexOutOfBoundsException if [index] is larger than or equal to [getMediaItemCount]
     */
    private fun updateMediaItemMetadata( index: Int, metadata: MediaMetadata ) {
        val upToDateMediaItem = getMediaItemAt( index ).buildUpon().setMediaMetadata( metadata ).build()
        replaceMediaItem( index, upToDateMediaItem )
    }

    private fun stopRadio() {
        radioJob.exchange( null )?.cancel()
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
        val scaledProgress = if( linearProgress <= 0f ) {
            0f
        } else if( linearProgress >= 1f ) {
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

        with( ValueAnimator.ofFloat(0f, 1f) ) {
            volumeAnimator = this

            duration = durationInMillis
            addUpdateListener { animator ->
                val floor = min( start, end )
                val ceil = max( start, end )

                volume = getVolumeForProgress(
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

    private fun startPlayback() {
        prepare()
        playWhenReady = true
    }

    //region Player
    override val currentMediaItemState: StateFlow<MediaItem?> = _currentMediaItemState.asStateFlow()
    override val repeatModeState: StateFlow<RepeatMode> = _currentRepeatModeState.asStateFlow()
    override val shuffleModeState: StateFlow<Boolean> = _currentShuffleModeState.asStateFlow()
    override val queueState: StateFlow<List<MediaItem>> = _queueState.asStateFlow()
    override val shouldBePlaying: Boolean
        get() = !(playbackState == MediaPlayer.STATE_ENDED || !playWhenReady)

    override fun startRadio() {
        logger.v { "Starting radio for currently playing media item: ${currentMediaItem?.mediaId}" }

        val mediaItem = currentMediaItem
        if( mediaItem == null ) {
            logger.w { "Can't start radio with no media item" }
            return
        }

        currentMediaItem

        startRadio( mediaItem )
    }

    override fun startRadio( mediaItem: MediaItem ) {
        logger.v { "Starting radio with media item ${mediaItem.mediaId} as seed" }

        // Always cancel old job before starting new one
        stopRadio()

        scope.launch {
            // TODO: Add check to filter out or apply workaround for local songs
            val results = withContext( Dispatchers.IO ) {
                youtube.getRadio( mediaItem.mediaId )
                       .onFailure { err ->
                           logger.e( "Failed to get radio", err )
                       }
                       .getOrDefault( emptyList() )
            }
            if( results.isEmpty() ) return@launch

            val songs = results.filter { it.id != mediaItem.mediaId }.map( InnertubeSong::toMediaItem )
            withContext( Dispatchers.Main ) {
                val totalCount = mediaItemCount
                addMediaItems( songs )
                removeMediaItems( nextMediaItemIndex, totalCount )
                if( currentMediaItemIndex > 0 )
                    removeMediaItems( 0, currentMediaItemIndex )
            }

            logger.d { "Radio completed with ${songs.size} songs appended to queue" }
        }.also( radioJob::store )
    }

    override fun cycleRepeatMode() {
        logger.v { "Advancing repeat mode" }

        val currentRepeatMode = repeatMode
        repeatMode = when( currentRepeatMode ) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_OFF
            // "else" shouldn't be executed at all,
            // if app crashes here, something went wrong, really wrong.
            else -> error( "Unknown repeat mode $currentRepeatMode" )
        }
    }

    override fun toggleShuffleMode() {
        logger.v { "Toggling shuffle mode" }
        shuffleModeEnabled = !shuffleModeEnabled
    }

    override fun addNext( mediaItem: MediaItem ) {
        logger.v { "Adding ${mediaItem.mediaId} to play next" }
        addMediaItem( nextMediaItemIndex, mediaItem )
    }

    override fun addNext( mediaItems: List<MediaItem> ) {
        logger.v { "Adding ${mediaItems.size} media items to next" }
        addMediaItems( nextMediaItemIndex, mediaItems )
    }

    override fun enqueue( mediaItem: MediaItem ) {
        logger.v { "Appending ${mediaItem.mediaId} to queue" }
        addMediaItem( mediaItemCount, mediaItem )
    }

    override fun enqueue( mediaItems: List<MediaItem> ) {
        logger.v { "Appending ${mediaItems.size} media items to queue" }
        addMediaItems( mediaItemCount, mediaItems )
    }

    override fun play( mediaItem: MediaItem ) {
        logger.v { "Force playing ${mediaItem.mediaId}" }

        stopRadio()
        setMediaItem( mediaItem )
        startPlayback()
    }

    override fun play( mediaItems: List<MediaItem>, startIndex: Int ) {
        logger.v { "Force playing ${mediaItems.size} media items, starting from $startIndex" }

        stopRadio()
        setMediaItems( mediaItems, startIndex, C.TIME_UNSET )
        startPlayback()
    }

    // TODO: Make this enable video mode
    override fun playVideo( mediaItem: MediaItem ) = play( mediaItem )

    override fun sleepTimerRemaining(): Flow<Long?> = flow { emit(null) }

    override fun stopSleepTimer() { /* Does nothing */ }

    override fun startSleepTimer( duration: Duration ) { /* Does nothing */ }
    //endregion

    /*
            ExoPlayer
     */

    //region ExoPlayer
    override fun getAudioSessionId(): Int = actualPlayer.audioSessionId

    override fun release() {
        logger.v { "Releasing player" }

        stopRadio()
        removeListener( this )
        actualPlayer.release()

        logger.d { "Player released" }
    }

    override fun addMediaItem( index: Int, mediaItem: MediaItem ) {
        require( index >= 0 ) { "index must be a non-zero integer" }

        logger.v { "Adding media item ${mediaItem.mediaId} to $index" }

        when( val mediaItemIndex = getMediaItemIndex(mediaItem.mediaId) ) {
            index -> {
                logger.i { "Existing media item and adding item are the same, updating metadata instead" }
                updateMediaItemMetadata( index, mediaItem.mediaMetadata )
            }

            // If [mediaItem] isn't in queue, add it normally
            -1 -> actualPlayer.addMediaItem( index, mediaItem )

            else -> {
                logger.i { "Media item exists in queue ($mediaItemIndex), moving it to $index" }

                // If [mediaItem] exists somewhere in the queue, move it to [index] and override metadata
                actualPlayer.moveMediaItem(mediaItemIndex, index)
                updateMediaItemMetadata( index, mediaItem.mediaMetadata )
            }
        }
    }

    // ExoPlayerImpl uses addMediaItems(index, mediaItems) internally
    override fun addMediaItems( mediaItems: List<MediaItem> ) = actualPlayer.addMediaItems( mediaItems )

    override fun addMediaItems( index: Int, mediaItems: List<MediaItem> ) {
        require( index >= 0 ) { "index must be a non-negative integer" }

        // If [mediaItems] contains currently playing MediaItem, extract it and update metadata
        val playingMediaItemIndex = mediaItems.indexOfFirst { it.mediaId == currentMediaItem?.mediaId }
        if( playingMediaItemIndex > -1 ) {
            // List.first is safe here because it's established that [currentMediaItem?.mediaId] is inside this list
            val metadata = mediaItems.first { it.mediaId == currentMediaItem?.mediaId }.mediaMetadata
            updateMediaItemMetadata( playingMediaItemIndex, metadata )
        }

        // All new media items must be unique (determined by their mediaId),
        // and must not contain currently playing media item.
        val filteredMediaItems = mediaItems.distinctBy( MediaItem::mediaId ).filter { it.mediaId != currentMediaItem?.mediaId }
        logger.d { "Adding ${filteredMediaItems.size}/${mediaItems.size} to $index" }

        // Copy current queue, remove all duplicates, and add [filteredMediaItems] to next position
        val queue =
            (0 until mediaItemCount).map( ::getMediaItemAt ).toMutableList().also { list ->
                // Capture unique IDs here also to speed up filter
                val appendingIds = mediaItems.map( MediaItem::mediaId ).toSet()
                list.removeIf { it.mediaId in appendingIds }

                val index = index.coerceAtMost( mediaItemCount )
                list.addAll( index, filteredMediaItems )
            }.toList()

        replaceMediaItems( 0, mediaItemCount, queue )
    }

    override fun play() {
        logger.v { "Resuming playback" }

        fun action() {
            if( playbackState == MediaPlayer.STATE_IDLE )
                prepare()
            actualPlayer.play()

            onIsPlayingChanged( true )
        }

        val duration = Preferences.AUDIO_FADE_DURATION.value.inWholeMilliseconds
        if( duration == 0L ) {
            logger.i { "Audio fade not set, play immediately" }

            action()
            return
        }

        startFade(
            start = 0f,
            end = volume,
            durationInMillis = duration,
            doOnStart = {
                logger.i { "Start audio fade effect, from 0 to $volume, duration $duration millis" }

                volume = 0f
                action()
            },
            doOnEnd = {
                logger.d { "Playback resumed" }
            }
        )
    }

    override fun pause() {
        logger.v { "Pausing playback" }

        val duration = Preferences.AUDIO_FADE_DURATION.value.inWholeMilliseconds
        if( duration == 0L ) {
            logger.i { "Audio fade not set, pause immediately" }

            actualPlayer.pause()
            return
        }

        val originalVolume = volume
        startFade(
            start = volume,
            end = 0f,
            durationInMillis = duration,
            doOnStart = {
                logger.i { "Start audio fade effect, from $volume to 0, duration $duration millis" }
            },
            doOnEnd = {
                actualPlayer.pause()
                volume = originalVolume

                logger.d { "Playback paused" }
            }
        )
    }

    override fun seekToPreviousMediaItem() {
        logger.v { "Seeking to previous media item" }

        val smartRewindSeconds = Preferences.SMART_REWIND.value
        if( smartRewindSeconds > 0 && currentPosition > smartRewindSeconds * 1000 ) {
            logger.v { "Smart rewind applied! Current position: $currentPosition, preference: $smartRewindSeconds" }
            seekTo( 0 )
        } else
            actualPlayer.seekToPreviousMediaItem()
        startPlayback()
    }

    override fun seekToPrevious() {
        logger.v { "Seeking to previous" }
        seekToPreviousMediaItem()
    }

    override fun seekToNextMediaItem() {
        logger.v { "Seeking to next media item" }

        actualPlayer.seekToNextMediaItem()
        startPlayback()
    }

    override fun seekToNext() {
        logger.v { "Seeking to next" }
        seekToNextMediaItem()
    }

    override fun seekToDefaultPosition( mediaItemIndex: Int ) {
        logger.v { "Jumping to media item at index $mediaItemIndex" }

        actualPlayer.seekToDefaultPosition( mediaItemIndex )
        startPlayback()
    }

    override fun seekTo( mediaItemIndex: Int, positionMs: Long ) {
        logger.v { "Jumping to $mediaItemIndex at $positionMs millis" }
        actualPlayer.seekTo( mediaItemIndex, positionMs )
        startPlayback()
    }

    override fun seekToDefaultPosition() {
        logger.v { "Seeking to default position of current media item" }

        actualPlayer.seekToDefaultPosition()
        startPlayback()
    }
    //endregion

    /*
            MediaPlayer.Listener
     */

    //region Listener
    private fun convertRepeatMode( @MediaPlayer.RepeatMode repeatMode: Int ) =
        when( repeatMode ) {
            REPEAT_MODE_ALL -> RepeatMode.ALL
            REPEAT_MODE_OFF -> RepeatMode.OFF
            REPEAT_MODE_ONE -> RepeatMode.ONE
            // Should never reach this else branch
            else -> error( "Unknown repeat mode $repeatMode" )
        }

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        _currentMediaItemState.value = mediaItem

        logger.d { "New media item: ${mediaItem?.mediaId}" }
    }

    override fun onRepeatModeChanged( repeatMode: Int ) {
        _currentRepeatModeState.value = convertRepeatMode( repeatMode )

        if( repeatMode != REPEAT_MODE_OFF ) {
            logger.i { "Repeat mode enabled! Disabling shuffle mode" }
            shuffleModeEnabled = false
        }

        logger.d { "Repeat mode updated to: $repeatMode" }
    }

    override fun onShuffleModeEnabledChanged( shuffleModeEnabled: Boolean ) {
        _currentShuffleModeState.value = shuffleModeEnabled

        if( shuffleModeEnabled ) {
            logger.i { "Shuffle mode enable! Disabling repeat mode" }
            repeatMode = REPEAT_MODE_OFF
        }

        logger.d { "Shuffle mode updated to: $shuffleModeEnabled" }
    }

    override fun onTimelineChanged( timeline: Timeline, reason: Int ) {
        logger.v { "Timeline updated due to: $reason. New size: ${timeline.windowCount}" }

        // Triggered by one of the following actions: addMediaItem, removeMediaItem,
        // moveMediaItem, setMediaItem, clearMediaItems, and replaceMediaItem
        if( reason == MediaPlayer.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED ) {
            val reusableWindow = Timeline.Window()
            // Create a list with fixed size to avoid reallocation
            val queue = buildList( timeline.windowCount ) {
                for( i in 0 until timeline.windowCount ) {
                    val mediaItem = timeline.getWindow(i, reusableWindow).mediaItem
                    add( mediaItem )
                }
            }
            _queueState.value = queue

            logger.d { "Updated queue to new timeline" }
        }
    }

    override fun onPlayerError( error: PlaybackException ) {
        logger.e( "Encountered playback error", error )
        stopRadio()
    }
    //endregion
}