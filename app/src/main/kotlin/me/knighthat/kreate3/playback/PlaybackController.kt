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
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** What plays again once it ends. */
enum class RepeatMode {
    /** Nothing, the queue stops after its last item. */
    OFF,

    /** The current item, over and over. */
    ONE,

    /** The whole queue, from its first item after its last. */
    ALL;

    /** The mode after this one when the user cycles through them: off, one, all, off again. */
    val next: RepeatMode
        get() = entries[(ordinal + 1) % entries.size]
}

/**
 * What the player is doing right now.
 *
 * @param current the item the queue is on, `null` when the queue is empty.
 * @param index where [current] is in [PlaybackController.queue], `-1` when the queue is empty.
 * @param isEnded whether the queue played to its end. A queue that is only loaded, like after
 * the app starts, or stopped by a failure, isn't ended.
 * @param isShuffled whether the queue plays in a random order. [PlaybackController.queue] keeps
 * its own order, [PlaybackController.shuffledOrder] is the one it plays in.
 * @param jumps how many times the position jumped instead of moving on by itself (seeks, previous
 * restarting the song, moving to another item), so a paused screen knows to read it again.
 */
data class PlaybackState(
    val current: MediaItem? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffled: Boolean = false,
    val jumps: Int = 0,
)

/**
 * How screens control playback, without knowing about the service or the player behind it.
 * Every function can be called from any thread and returns at once.
 */
interface PlaybackController {

    val state: StateFlow<PlaybackState>

    /** Every item of the queue, in its own order, see [shuffledOrder] for the one they play in. */
    val queue: StateFlow<List<MediaItem>>

    /**
     * Positions of [queue]'s items in the order they play while shuffled, `null` while they
     * play in the queue's own order. For a moment after the queue changes, it may still be the
     * order of the queue before.
     */
    val shuffledOrder: StateFlow<List<Int>?>

    /** When playback pauses by itself, `null` while the sleep timer is off. */
    val sleepTimer: StateFlow<SleepTimer?>

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
     * Adds [items] to the end of a queue started from [seed] with [playAll], like the rest of a
     * list fetched while [seed] already plays. Nothing is added when the queue was changed to
     * something else meanwhile.
     */
    fun follow(seed: MediaItem, items: List<MediaItem>)

    /**
     * Adds [items] right after the current item, in their order, without starting to play. With
     * an empty queue they become the queue.
     */
    fun playNext(items: List<MediaItem>)

    /**
     * Takes back what [enqueue] or [playNext] added, [items] being the list it was given: the
     * items where they were added, or the last copy of each song once the queue changed around
     * them. Like [remove], taking the current item out moves on first.
     */
    fun unqueue(items: List<MediaItem>)

    /**
     * Puts [items] at [index] of [queue], in their order, at its end when it is shorter, without
     * starting to play. With an empty queue they become the queue.
     */
    fun insert(index: Int, items: List<MediaItem>)

    /**
     * Replaces the queue with [items], at the item [index] from [positionMs], playing when
     * [play], like putting back a queue [stop] emptied.
     */
    fun restore(items: List<MediaItem>, index: Int, positionMs: Long, play: Boolean)

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

    /**
     * Takes every item of the song [mediaId] out of the queue for good, like a song the user
     * disliked. When the current item is one of them, the next item plays first, or the player
     * stops when none comes next. Unlike [next], it never stays on it, nor comes back to it when
     * the queue repeats.
     */
    fun drop(mediaId: String)

    /**
     * Takes the item at [index] of [queue] out of it. Removing the current item moves on to the
     * next one first, the queue ends when none comes next.
     */
    fun remove(index: Int)

    /**
     * Takes every item but the current one out of the queue, it keeps playing alone. Songs a
     * radio is still looking for aren't added anymore. To empty the queue too, see [stop].
     */
    fun clear()

    /** Moves the item at [from] of [queue] to [to], the items between shift over by one. */
    fun move(from: Int, to: Int)

    /** Plays the item at [index] of [queue] from its start. */
    fun skipTo(index: Int)

    /**
     * Goes back to the start of the current item, or to the item before it when the current one
     * has only just started.
     */
    fun previous()

    /** Moves to [positionMs] milliseconds into the current item. */
    fun seekTo(positionMs: Long)

    /** Turns playing in a random order on or off. Turning it on stops repeating one item. */
    fun toggleShuffle()

    /**
     * Moves to the [RepeatMode] after the current one, see [RepeatMode.next]. Repeating one item
     * turns shuffling off.
     */
    fun cycleRepeat()

    /** Stops playing and empties the queue, nothing is left to resume. */
    fun stop()

    /**
     * Starts a sleep timer that pauses playback in [duration], or as the song playing ends when
     * `null`, in place of the one running. Emptying the queue turns it off.
     */
    fun startSleepTimer(duration: Duration?)

    /** Does [action] to the sleep timer running, nothing without one. */
    fun changeSleepTimer(action: SleepTimerAction)

    /**
     * Id of the player's audio session, for an equalizer to work on, `null` while it isn't
     * known. Only from the main thread.
     */
    fun audioSessionId(): Int?
}
