package app.kreate.player

import androidx.annotation.AnyThread
import kotlinx.coroutines.flow.StateFlow


/**
 * Unless annotated with [AnyThread], or return type derived from Kotlin coroutine,
 * all functions and fields are expected to be called on Main thread.
 *
 * `Playlist` referred in this class is the
 */
expect interface Player {

    val repeatModeState: StateFlow<RepeatMode>
    val shuffleModeState: StateFlow<Boolean>
    val currentMediaItemState: StateFlow<MediaItem?>
    val queueState: StateFlow<List<MediaItem>>
    val queue: List<MediaItem>

    /**
     * Clears playlist, except for the currently playing [MediaItem]. Then use the currently
     * playing [MediaItem] as seed to get similar songs and append them to new playlist.
     *
     * If there's no active [MediaItem] in current playlist, this operation will be skipped.
     */
    fun startRadio()

    /**
     * Clears the playlist, adds the specified [mediaItem] and resets the position to the
     * default position. Then use [mediaItem] as seed to get similar songs and append
     * them to new playlist.
     *
     * This function can be called on any thread.
     *
     * @param mediaItem the new [MediaItem]
     */
    @AnyThread
    fun startRadio( mediaItem: MediaItem )

    /**
     * Adds [mediaItem] next to currently playing index.
     *
     * If [mediaItem] is currently playing [MediaItem], this call will be ignored.
     *
     * If [mediaItem] exists in the current playlist, it'll be moved.
     */
    fun addNext( mediaItem: MediaItem )

    /**
     * Adds [mediaItems] next to currently playing index.
     *
     * If [mediaItems] contains currently playing [MediaItem], that item will be skipped.
     *
     * If [mediaItems] exist in the current playlist, they'll be moved.
     */
    fun addNext( mediaItems: List<MediaItem> )

    /**
     * Adds [mediaItem] to the end of playlist.
     *
     * If [mediaItem] exists in current playlist and not at the bottom, it'll be moved.
     */
    fun enqueue( mediaItem: MediaItem )

    /**
     * Adds [mediaItems] to the end of playlist.
     *
     * If [mediaItems] exist in current playlist and not at the bottom, they'll be moved.
     */
    fun enqueue( mediaItems: List<MediaItem> )

    /**
     * Starts playback, if [Preferences.AUDIO_FADE_DURATION] is set to anything larger
     * than 0 then fading effect will be applied.
     */
    fun play()

    /**
     * Clears the playlist, adds the specified [MediaItem] and resets the position to the
     * default position, then starts playback.
     *
     * @param mediaItem The new [MediaItem].
     *
     * @see [play] for more information.
     */
    fun play( mediaItem: MediaItem )

    /**
     * Clears the playlist and adds the specified [mediaItems], then starts playback.
     *
     * @param mediaItems The new playlist.
     * @param startIndex The [MediaItem] index to start playback from. If `-1`
     *     is passed, the current position is not reset. Default is 0, play from start.
     *
     * @throws IllegalStateException If the provided [startIndex] is not within the
     *     bounds of the list of media items.
     */
    fun play( mediaItems: List<MediaItem>, startIndex: Int = 0 )

    fun playVideo( mediaItem: MediaItem )

    /**
     * Stops playback, if [Preferences.AUDIO_FADE_DURATION] is set to anything larger
     * than 0 then fading effect will be applied.
     */
    fun pause()

    /**
     * Returns whether a previous media item exists, which may depend on the current repeat mode and
     * whether shuffle mode is enabled.
     *
     * **Note**: When the repeat mode is [RepeatMode.ONE], this method behaves the same as when
     * the current repeat mode is [RepeatMode.OFF].
     */
    fun hasPreviousMediaItem(): Boolean

    /**
     * Seeks to the default position of the previous [MediaItem], which may depend on the
     * current repeat mode and whether shuffle mode is enabled. Does nothing if
     * [hasPreviousMediaItem] is `false`.
     *
     * Sets position to default if [hasPreviousMediaItem] is `false`.
     *
     * Sets position to default if [Preferences.SMART_REWIND] is non-zero and current position
     * exceeds indicated value. Otherwise, seek to previous [MediaItem]
     *
     * **Note**: When the repeat mode is [RepeatMode.ONE], this method behaves the same as when
     * the current repeat mode is [RepeatMode.OFF].
     */
    fun seekToPreviousMediaItem()

    /**
     * Returns whether a next [MediaItem] exists, which may depend on the current repeat mode
     * and whether shuffle mode is enabled.
     *
     * **Note**: When the repeat mode is [RepeatMode.ONE], this method behaves the same as when
     * the current repeat mode is [RepeatMode.OFF].
     */
    fun hasNextMediaItem(): Boolean

    /**
     * Seeks to the default position of the next [MediaItem], which may depend on the current
     * repeat mode and whether shuffle mode is enabled. Does nothing if [hasNextMediaItem] is
     * `false`.
     *
     * **Note**: When the repeat mode is [RepeatMode.ONE], this method behaves the same as when
     * the current repeat mode is [RepeatMode.OFF].
     */
    fun seekToNextMediaItem()
}