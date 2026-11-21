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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Grows the queue of [player] as it plays: while [state] holds a [QueueContinuation] and
 * [lookahead] songs or fewer are left to play after the one playing, in the play order, the next
 * part is fetched and added to the end, and the continuation moves on to the one after it. Nothing
 * is fetched while the player isn't meant to play. A part that fails is asked again on the next
 * change of the song or the queue. Use it on the main thread, [scope] has to run there.
 *
 * @param lookahead songs left at which the next part is fetched, read at each check
 * @param fetch the part after a continuation, empty when there is none
 */
class QueueExtender(
    private val player: Player,
    private val scope: CoroutineScope,
    private val state: QueueContinuationState,
    private val lookahead: () -> Int,
    private val fetch: suspend (QueueContinuation) -> QueuePart,
) : Player.Listener {

    private val window = Timeline.Window()
    private var fetching = false

    init {
        player.addListener(this)
        // A queue that starts with a continuation may already be short, once its songs are in:
        // they come with a change of the queue that checks again
        state.current.onEach { if (player.mediaItemCount >= state.readyAt) check() }
            .launchIn(scope)
        // Somebody scrolled to the end of the queue: now, playing or not
        state.requests.onEach { state.current.value?.let(::extend) }.launchIn(scope)
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

    /** Fetches the next part when few enough songs are left. */
    private fun check() {
        val continuation = state.current.value ?: return
        val timeline = player.currentTimeline
        if (fetching || timeline.isEmpty || !player.playWhenReady) return

        // Walks the songs after the one playing in their play order, never round again: running
        // out of them within [lookahead] steps means few are left
        var index = player.currentMediaItemIndex
        repeat(lookahead().coerceAtLeast(1) + 1) {
            index = timeline.getNextWindowIndex(
                index,
                Player.REPEAT_MODE_OFF,
                player.shuffleModeEnabled,
            )
            if (index == C.INDEX_UNSET) return extend(continuation)
        }
    }

    private fun extend(continuation: QueueContinuation) {
        if (fetching) return
        fetching = true
        state.setLoading(true)
        scope.launch {
            try {
                val part = fetch(continuation)
                // The queue was replaced meanwhile, the part isn't for it
                if (state.current.value != continuation) return@launch

                val items = if (continuation is QueueContinuation.Similar) {
                    // Asking again gives songs that may be there already
                    val queued = (0 until player.mediaItemCount)
                        .mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
                    part.items.filter { it.mediaId !in queued }
                } else {
                    part.items
                }
                if (items.isNotEmpty()) player.addMediaItems(items)
                // Nothing new means nothing more to ask for
                val next = part.next.takeIf { items.isNotEmpty() }
                state.advance(continuation, next)
                Logger.d(tag = TAG) { "Queued ${items.size} more songs of ${continuation::class.simpleName}" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kept, the next change asks again
                Logger.w(
                    e,
                    TAG
                ) { "Couldn't fetch the next songs of ${continuation::class.simpleName}" }
            } finally {
                fetching = false
                state.setLoading(false)
            }
        }
    }

    private companion object {

        const val TAG = "QueueExtender"
    }
}
