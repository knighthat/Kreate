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
package me.knighthat.kreate3.source.youtube

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.knighthat.kreate3.preferences.Preferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Paces the searches sent to YouTube for many songs at once, like an import's songs YouTube no
 * longer has, so YouTube doesn't take the app for a bot. A single search, like the one for a
 * song that stopped playing, doesn't come here: it is sent at once.
 *
 * Each batch ([searchAll]) runs in a worker started on the app's [scope], not tied to a screen.
 * Batches queue: one asked for while another runs starts once that one and every batch asked
 * for before it have finished. The searches go one at a time and at most
 * [Preferences.YOUTUBE_SEARCHES_PER_SECOND] of them start in a second; it is read before each
 * search, so a change applies to the next one.
 */
class BatchSearches(private val scope: CoroutineScope) {

    private val turn = Mutex()

    // When the last search of any batch started, only touched while holding turn
    private var lastStart: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * [search] for each of [items], in their order, paced as the class says, after the batches
     * asked for before it. Cancelling the caller stops its worker before the next search, or takes
     * it out of the queue while it waits.
     *
     * @return what [search] gave for each of [items], in their order
     */
    suspend fun <T, R> searchAll(items: List<T>, search: suspend (T) -> R): List<R> {
        if (items.isEmpty()) return emptyList()

        // Undispatched, it asks for its turn before this returns: batches queue in call order
        val worker = scope.async(start = CoroutineStart.UNDISPATCHED) {
            turn.withLock {
                val perSecond = searchesPerSecond()
                Logger.i(tag = TAG) {
                    "Searching for ${items.size} songs, at most $perSecond a second: about " +
                            "${searchTime(items.size, perSecond).inWholeSeconds} s"
                }
                items.map { item ->
                    lastStart?.let { delay(gap() - it.elapsedNow()) }
                    lastStart = TimeSource.Monotonic.markNow()
                    search(item)
                }
            }
        }
        return try {
            worker.await()
        } catch (e: CancellationException) {
            worker.cancel()
            throw e
        }
    }

    private fun gap(): Duration = 1.seconds / searchesPerSecond()

    private companion object {
        const val TAG = "BatchSearches"
    }
}

/** [Preferences.YOUTUBE_SEARCHES_PER_SECOND], at least one. */
fun searchesPerSecond(): Int = Preferences.YOUTUBE_SEARCHES_PER_SECOND.value.coerceAtLeast(1)

/** About how long [BatchSearches] takes to search for [searches] songs at [perSecond]. */
fun searchTime(searches: Int, perSecond: Int): Duration =
    (searches * 1000L / perSecond.coerceAtLeast(1)).milliseconds
