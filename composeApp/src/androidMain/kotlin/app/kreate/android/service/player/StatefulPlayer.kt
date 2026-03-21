package app.kreate.android.service.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.database.models.Song
import it.fast4x.innertube.models.NavigationEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface StatefulPlayer : ExoPlayer {

    val currentMediaItemState: StateFlow<MediaItem?>
    val currentTimelineState: StateFlow<Timeline>
    val currentWindowState: StateFlow<Timeline.Window?>
    val isPlayingState: StateFlow<Boolean>

    fun isLoadingRadio(): Boolean

    fun startRadio()

    /**
     * Start a new queue with [mediaItem] the first to play.
     * Other songs follow [mediaItem]'s genre or mood
     */
    fun startRadio(
        mediaItem: MediaItem,
        append: Boolean = false,
        endpoint: NavigationEndpoint.Endpoint.Watch? = null
    )

    /**
     * Start a new queue with [song] the first to play.
     * Other songs follow [song]'s genre or mood
     */
    fun startRadio(
        song: Song,
        append: Boolean = false,
        endpoint: NavigationEndpoint.Endpoint.Watch? = null
    )

    fun stopRadio()

    /**
     * Off -> One
     * One -> All
     * All -> Off
     *
     * Enabling repeat mode disables shuffle mode
     */
    fun cycleRepeatMode()

    /**
     * Should next song in queue be random.
     *
     * Enabling shuffle mode disables repeat mode
     */
    fun toggleShuffleMode()

    /**
     * Starts a countdown internally.
     *
     * When it reaches **0**, player will stop playing music
     */
    fun startSleepTimer( duration: Duration )

    /**
     * Cancel running sleep timer (if active)
     */
    fun stopSleepTimer()

    /**
     * @return remaining time of **sleep timer**. `null` if **sleep timer** isn't active
     */
    fun sleepTimerRemaining(): Flow<Long?>

    @UnstableApi
    fun toForwardingPlayer(): ForwardingPlayer
}