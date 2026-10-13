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

package me.knighthat.kreate.playback

import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player is doing right now.
 *
 * @param current the item the queue is on, `null` when the queue is empty.
 */
data class PlaybackState(
    val current: MediaItem? = null,
    val isPlaying: Boolean = false,
)

/**
 * How screens control playback, without knowing about the service or the player behind it.
 * Every function can be called from any thread and returns at once.
 */
interface PlaybackController {

    val state: StateFlow<PlaybackState>

    /**
     * Plays [item] now. It is added to the end of the queue, unless it is already in it, and
     * the queue then jumps to it.
     */
    fun play(item: MediaItem)

    /**
     * How far into the current item, from `0` to `1`; `0` when unknown. Cheap enough to read
     * every frame, but only from the main thread.
     */
    fun progress(): Float

    /** Pauses while playing, plays otherwise. */
    fun togglePlay()

    /** Jumps to the next item of the queue, if any. */
    fun next()
}
