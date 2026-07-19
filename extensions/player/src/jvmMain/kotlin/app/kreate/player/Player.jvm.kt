package app.kreate.player

import androidx.annotation.AnyThread
import kotlinx.coroutines.flow.StateFlow

actual interface Player {

    actual val repeatModeState: StateFlow<RepeatMode>
    actual val shuffleModeState: StateFlow<Boolean>
    actual val currentMediaItemState: StateFlow<MediaItem?>
    actual val queueState: StateFlow<List<MediaItem>>

    actual fun startRadio()

    @AnyThread
    actual fun startRadio( mediaItem: MediaItem )

    actual fun addNext( mediaItem: MediaItem )

    actual fun addNext( mediaItems: List<MediaItem> )

    actual fun enqueue( mediaItem: MediaItem )

    actual fun enqueue( mediaItems: List<MediaItem> )

    actual fun play()

    actual fun play( mediaItem: MediaItem )

    actual fun play( mediaItems: List<MediaItem>, startIndex: Int )

    actual fun playVideo( mediaItem: MediaItem )

    actual fun pause()

    actual fun hasPreviousMediaItem(): Boolean

    actual fun seekToPreviousMediaItem()

    actual fun hasNextMediaItem(): Boolean

    actual fun seekToNextMediaItem()
}