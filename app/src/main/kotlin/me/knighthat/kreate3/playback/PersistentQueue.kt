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

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.QueueRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.toMediaItem
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the queue of [player] in the database while [Preferences.PERSISTENT_QUEUE_ENABLED] is on,
 * and puts it back when the player is made with an empty queue.
 *
 * A change to the queue saves it whole at once, with where the song playing is. Moving to another
 * song, pausing and seeking save where the song is, and so does every
 * [Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL] while it plays. Songs missing from the database
 * aren't saved.
 *
 * Use it on the main thread, [scope] has to run there. The writes run in [writeScope], which has
 * to outlive the player: [release] sends the last one, call it before releasing the player.
 */
class PersistentQueue(
    private val player: Player,
    private val queue: QueueRepository,
    private val songs: UserSongRepository,
    scope: CoroutineScope,
    writeScope: CoroutineScope,
) : Player.Listener {

    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val following: Job

    private val enabled: Boolean
        get() = Preferences.PERSISTENT_QUEUE_ENABLED.value

    init {
        writeScope.launch { write() }
        following = scope.launch {
            if (enabled && player.mediaItemCount == 0) restore()
            player.addListener(this@PersistentQueue)

            combine(
                Preferences.PERSISTENT_QUEUE_ENABLED.state,
                Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.state,
                ::Pair,
            ).collectLatest { (enabled, interval) ->
                if (!enabled) return@collectLatest
                // Catches up with what changed while it was off. The restored queue is saved again
                // too: songs that couldn't be restored are gone, the places must match the player.
                // An empty player saves nothing, so a queue that failed to restore isn't wiped.
                if (player.mediaItemCount > 0) saveQueue()
                while (true) {
                    delay(interval.coerceAtLeast(1).seconds)
                    if (player.isPlaying) savePosition()
                }
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && enabled) saveQueue()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A change to the queue has saved it already
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && enabled)
            savePosition()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying && enabled) savePosition()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK && enabled) savePosition()
    }

    /**
     * Saves the queue again, like after one of its songs was stored: it was left out until then.
     */
    fun refresh() {
        if (enabled && player.mediaItemCount > 0) saveQueue()
    }

    /** Saves where the song playing is and stops. */
    fun release() {
        following.cancel()
        player.removeListener(this)
        if (enabled) savePosition()
        writes.close()
    }

    /**
     * Queues the saved songs, at the song that was playing and where it was. Songs that can't be
     * played anymore are left out. Nothing is restored if something was queued meanwhile.
     */
    private suspend fun restore() {
        val saved = try {
            queue.list()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the saved queue", e)
            return
        }

        val items = ArrayList<MediaItem>(saved.size)
        var start = 0
        var position = 0L
        for (song in saved) {
            val item = songs.find(song.songId).first()?.toMediaItem()?.withSongUri() ?: continue
            song.position?.let {
                start = items.size
                position = it
            }
            items += item
        }

        if (items.isEmpty() || player.mediaItemCount > 0) return
        // Not prepared, nothing loads until the user plays
        player.setMediaItems(items, start, position)
        Log.d(TAG, "Restored ${items.size} of ${saved.size} songs")
    }

    private fun saveQueue() {
        val songIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        writes.trySend(Write.Queue(songIds, player.currentMediaItemIndex, player.currentPosition))
    }

    private fun savePosition() {
        if (player.mediaItemCount == 0) return
        writes.trySend(Write.Position(player.currentMediaItemIndex, player.currentPosition))
    }

    /** Writes in the order they were sent, a position belongs to the queue saved before it. */
    private suspend fun write() {
        for (write in writes)
            try {
                when (write) {
                    is Write.Queue -> queue.replace(write.songIds, write.current, write.position)
                    is Write.Position -> queue.setPosition(write.index, write.position)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the queue", e)
            }
    }

    private sealed interface Write {

        class Queue(val songIds: List<String>, val current: Int, val position: Long) : Write

        class Position(val index: Int, val position: Long) : Write
    }

    private companion object {

        const val TAG = "PersistentQueue"
    }
}
