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

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Songs left before the one carrying a [QueueTail] at which its next part is fetched. */
internal const val TAIL_LOOKAHEAD = 5

/**
 * Grows the queue of [player] as it plays: when a song carrying a [QueueTail] is
 * [TAIL_LOOKAHEAD] songs or fewer away in the play order, the part after it is fetched and put
 * right after it, and the tail moves on to the part's last song. Nothing is fetched while the
 * player isn't meant to play, like a queue restored at start. A part that fails is asked again on
 * the next song change. Use it on the main thread, [scope] has to run there.
 *
 * @param fetch the songs after [QueueTail.token] as the player queues them, the last one carrying
 * the next tail, empty when there are none
 */
class QueueExtender(
    private val player: Player,
    private val scope: CoroutineScope,
    private val fetch: suspend (QueueTail) -> List<MediaItem>,
) : Player.Listener {

    private val window = Timeline.Window()

    /** Tokens being fetched, each asked once at a time. */
    private val fetching = mutableSetOf<String>()

    init {
        player.addListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = check()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) check()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = check()

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = check()

    fun release() {
        player.removeListener(this)
    }

    /** Fetches the parts whose tail is close enough to the song playing. */
    private fun check() {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || !player.playWhenReady) return
        var index = player.currentMediaItemIndex
        // The song playing and the ones after it, in the order they play, never round again
        repeat(TAIL_LOOKAHEAD + 1) {
            if (index == C.INDEX_UNSET) return
            timeline.getWindow(index, window).mediaItem.queueTail
                ?.takeIf { it.token !in fetching }
                ?.let(::extend)
            index = timeline.getNextWindowIndex(
                index,
                Player.REPEAT_MODE_OFF,
                player.shuffleModeEnabled,
            )
        }
    }

    private fun extend(tail: QueueTail) {
        fetching += tail.token
        scope.launch {
            try {
                val part = fetch(tail)
                // The queue may have changed meanwhile, the song carrying it is looked up again
                val at = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).queueTail == tail }
                    ?: return@launch
                if (part.isNotEmpty()) player.addMediaItems(at + 1, part)
                player.replaceMediaItem(at, player.getMediaItemAt(at).withQueueTail(null))
                Logger.d(tag = TAG) { "Queued ${part.size} more songs of ${tail.playlistId}" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kept, the next song change asks again
                Logger.w(e, TAG) { "Couldn't fetch the next songs of ${tail.playlistId}" }
            } finally {
                fetching -= tail.token
            }
        }
    }

    private companion object {

        const val TAG = "QueueExtender"
    }
}
