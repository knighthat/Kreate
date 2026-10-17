/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.playback

import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player is doing right now.
 *
 * @param current the item the queue is on, `null` when the queue is empty.
 * @param index where [current] is in [PlaybackController.queue], `-1` when the queue is empty.
 * @param isEnded whether the queue played to its end. A queue that is only loaded, like after
 * the app starts, or stopped by a failure, isn't ended.
 */
data class PlaybackState(
    val current: MediaItem? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
)

/**
 * How screens control playback, without knowing about the service or the player behind it.
 * Every function can be called from any thread and returns at once.
 */
interface PlaybackController {

    val state: StateFlow<PlaybackState>

    /** Every item of the queue, in the order they play. */
    val queue: StateFlow<List<MediaItem>>

    /**
     * What to tell the user each time a song fails to play, like `Couldn't play "Song": its file
     * is gone`. A failure that nobody collects is dropped.
     */
    val errors: Flow<String>

    /**
     * Plays [item] now. It is added to the end of the queue, unless it is already in it, and
     * the queue then jumps to it.
     */
    fun play(item: MediaItem)

    /** Replaces the queue with [items] and plays them from the first. */
    fun playAll(items: List<MediaItem>)

    /**
     * Fills the queue with songs like [seed] once they are found: from the library for a song of
     * the device, from the source for any other. When nothing is found, or the source can't be
     * reached, nothing is added. Calling it again drops the songs still being searched for.
     *
     * @param append `true` to keep the queue and add the songs found to its end, skipping the
     * ones already in it, without changing what plays. `false` to replace the queue with [seed]
     * and play it at once, the songs found then follow it, unless the queue was changed to
     * something else meanwhile.
     */
    fun radio(seed: MediaItem, append: Boolean = false)

    /** Adds [items] to the end of the queue, without starting to play. */
    fun enqueue(items: List<MediaItem>)

    /**
     * How far into the current item, from `0` to `1`; `0` when unknown. Cheap enough to read
     * every frame, but only from the main thread.
     */
    fun progress(): Float

    /** Pauses while playing, plays otherwise. After a failure, it tries the song again. */
    fun togglePlay()

    /**
     * Where the player is in the current item, in milliseconds, `0` when nothing is. Like
     * [progress], cheap enough to read every frame from the main thread.
     */
    fun position(): Long

    /** Length of the current item in milliseconds, `null` while it isn't known. */
    fun duration(): Long?

    /** Jumps to the next item of the queue, if any. */
    fun next()

    /** Plays the item at [index] of [queue] from its start. */
    fun skipTo(index: Int)

    /**
     * Goes back to the start of the current item, or to the item before it when the current one
     * has only just started.
     */
    fun previous()

    /** Moves to [positionMs] milliseconds into the current item. */
    fun seekTo(positionMs: Long)
}
