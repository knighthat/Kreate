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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.QueueRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.toMediaItem
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the queue of [player] in the database while [Preferences.PERSISTENT_QUEUE_ENABLED] is on,
 * and puts it back when the player is made with an empty queue.
 *
 * A change to the queue is written as it happens, only the songs it touches (and the places they
 * push, see [diffQueue]), whether or not the setting is on, so turning it on finds the changes
 * until then known.
 * The whole queue is saved when this starts or the setting is turned on. Moving to another
 * song, pausing and seeking save where the song is, and so does every
 * [Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL] while it plays. The saved queue only points at
 * stored songs: a remote one not in the shared catalog is stored there before it is saved, which
 * asks its source once for it. It stays out of the user's library until it is played or kept, and
 * is read back with the user's own values where they have any. A song that can't be stored isn't
 * saved.
 *
 * Use it on the main thread, [scope] has to run there. The writes run in [writeScope], which has
 * to outlive the player: [release] sends the last one, call it before releasing the player.
 *
 * @param store makes sure a remote song is in the catalog, see [QueueWriter]
 * @param queued a saved song as the player queues it, like the session does with the songs added
 * to it; `null` leaves it out
 */
class PersistentQueue(
    private val player: Player,
    private val queue: QueueRepository,
    private val scope: CoroutineScope,
    writeScope: CoroutineScope,
    store: suspend (songId: String, origin: Origin) -> Unit = { _, _ -> },
    private val queued: (MediaItem) -> MediaItem? = { it.withSongUri() },
) : Player.Listener {

    private val writer = QueueWriter(queue, store, writeScope) {
        // The saved queue doesn't match the player's anymore, send it whole again
        scope.launch { if (enabled && player.mediaItemCount > 0) saveQueue() }
    }
    private val window = Timeline.Window()

    /** The queue as the last change left it, the uid of each window. */
    private var known: List<Any> = emptyList()
    private val following: Job

    /** Completes once the saved queue was put back, or wasn't to be. */
    private val restored = CompletableDeferred<Unit>()

    private val enabled: Boolean
        get() = Preferences.PERSISTENT_QUEUE_ENABLED.value

    init {
        following = scope.launch {
            try {
                if (enabled && player.mediaItemCount == 0) restore()
            } finally {
                restored.complete(Unit)
            }
            known = uids(player.currentTimeline)
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
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return

        val old = known
        known = uids(timeline)
        if (!enabled) return

        val changes = diffQueue(old, known)
        if (changes.isEmpty()) return

        for (change in changes)
            when (change) {
                is QueueChange.Remove ->
                    writer.send(QueueWrite.Remove(change.index, change.count))

                is QueueChange.Move ->
                    writer.send(QueueWrite.Move(change.index, change.count, change.to))

                is QueueChange.Insert -> writer.send(
                    QueueWrite.Insert(change.index, entries(timeline, change.index, change.count))
                )

                QueueChange.Replace -> {
                    saveQueue()
                    return
                }
            }

        // The song playing may be another one now, or have moved: the changes carry no place
        savePosition()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A change to the queue wrote where the song is already
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
        writer.close()
    }

    /**
     * The saved songs as the player queues them, at the song that was playing and where it was.
     * Songs that can't be played anymore are left out.
     *
     * @param currentOnly whether to give only the song that was playing
     * @return `null` while [Preferences.PERSISTENT_QUEUE_ENABLED] is off, or when nothing saved
     * can be played
     */
    suspend fun saved(currentOnly: Boolean = false): Saved? {
        if (!enabled) return null
        val saved = try {
            queue.details()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the saved queue", e)
            return null
        }

        val items = ArrayList<MediaItem>(if (currentOnly) 1 else saved.size)
        var start = 0
        var position = 0L
        for (song in saved) {
            if (currentOnly && song.position == null) continue
            val item = song.song.toMediaItem()?.let(queued) ?: continue
            song.position?.let {
                start = items.size
                position = it
            }
            items += item
        }
        return if (items.isEmpty()) null else Saved(items, start, position)
    }

    /** Queues the [saved] songs. Nothing is restored if something was queued meanwhile. */
    private suspend fun restore() {
        val saved = saved() ?: return
        if (player.mediaItemCount > 0) return
        // Not prepared, nothing loads until the user plays
        player.setMediaItems(saved.items, saved.start, saved.position)
        Log.d(TAG, "Restored ${saved.items.size} songs")
    }

    /**
     * Waits until the saved queue was put back, or wasn't to be. A change to the queue made
     * before could land before the restored songs, or be taken for them: a song added to an
     * empty player is only there once the session has handled it.
     */
    suspend fun awaitRestored() = restored.await()

    /** Songs to queue, starting at [start] from [position] ms. */
    class Saved(val items: List<MediaItem>, val start: Int, val position: Long)

    private fun saveQueue() {
        val songs = List(player.mediaItemCount) {
            val item = player.getMediaItemAt(it)
            QueueEntry(item.mediaId, item.origin)
        }
        writer.send(QueueWrite.Queue(songs, player.currentMediaItemIndex, player.currentPosition))
    }

    private fun uids(timeline: Timeline): List<Any> =
        List(timeline.windowCount) { timeline.getWindow(it, window).uid }

    private fun entries(timeline: Timeline, from: Int, count: Int): List<QueueEntry> =
        List(count) {
            val item = timeline.getWindow(from + it, window).mediaItem
            QueueEntry(item.mediaId, item.origin)
        }

    private fun savePosition() {
        if (player.mediaItemCount == 0) return
        writer.send(QueueWrite.Position(player.currentMediaItemIndex, player.currentPosition))
    }

    private companion object {

        const val TAG = "PersistentQueue"
    }
}
