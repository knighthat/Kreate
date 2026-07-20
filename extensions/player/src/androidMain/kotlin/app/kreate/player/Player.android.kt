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

    fun cycleRepeatMode()

    fun toggleShuffleMode()

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