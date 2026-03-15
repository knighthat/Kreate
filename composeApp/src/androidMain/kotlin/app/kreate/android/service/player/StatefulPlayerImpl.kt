package app.kreate.android.service.player

import android.animation.Animator
import android.animation.ValueAnimator
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.Preferences
import app.kreate.android.service.PlayerEventUpdateDiscord
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


/**
 * A custom ExoPlayer with additional features:
 * - Fading effect
 * - Observable states (current mediaItem, timeline, window, etc.)
 */
@OptIn(UnstableApi::class)
class StatefulPlayerImpl(
    private val player: ExoPlayer
): ExoPlayer by player, StatefulPlayer, Player.Listener, KoinComponent {

    private val _currentMediaItemState = MutableStateFlow<MediaItem?>(null)
    private val _currentTimelineState = MutableStateFlow(Timeline.EMPTY)
    private val _currentWindowState = MutableStateFlow<Timeline.Window?>(null)

    private var volumeAnimator: ValueAnimator? = null

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
}