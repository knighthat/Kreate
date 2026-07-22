package app.kreate.player

import androidx.annotation.AnyThread
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow


actual interface Player : ExoPlayer {

    val shouldBePlaying: Boolean

    actual val repeatModeState: StateFlow<RepeatMode>
    actual val shuffleModeState: StateFlow<Boolean>
    actual val currentMediaItemState: StateFlow<MediaItem?>
    actual val queueState: StateFlow<List<MediaItem>>

    /**
     * Return a wrapper to actual queue. This operation is extremely fast, because [MediaItem]
     * aren't being extracted.
     *
     * This list is virtual, any read/write to this list must happen on main thread,
     * see [getApplicationLooper]. If you need a copy of queue, see [captureQueue]
     */
    actual val queue: List<MediaItem>

    fun cycleRepeatMode()

    fun toggleShuffleMode()

    fun shuffleQueue()

    /**
     * This method must be called on main thread. See [getApplicationLooper]
     *
     * @return a list of all [MediaItem] inside current queue. List created by this function
     *   is safe to use in other threads
     */
    fun captureQueue( from: Int = 0, to: Int = mediaItemCount ): List<MediaItem>

    actual fun startRadio()

    @AnyThread
    actual fun startRadio( mediaItem: MediaItem )

    actual fun addNext( mediaItem: MediaItem )

    actual fun addNext( mediaItems: List<MediaItem> )

    actual fun enqueue( mediaItem: MediaItem )

    actual fun enqueue( mediaItems: List<MediaItem> )

    actual fun play( mediaItem: MediaItem )

    actual fun play( mediaItems: List<MediaItem>, startIndex: Int )

    actual fun playVideo( mediaItem: MediaItem )

    actual override fun play()

    actual override fun pause()

    actual override fun hasPreviousMediaItem(): Boolean

    actual override fun seekToPreviousMediaItem()

    actual override fun hasNextMediaItem(): Boolean

    actual override fun seekToNextMediaItem()
}