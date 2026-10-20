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

package me.knighthat.kreate3.lyrics

import android.util.Log
import kotlinx.coroutines.CancellationException
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync

private const val TAG = "LyricsFinder"

/**
 * No source had lyrics for the song, and at least one couldn't be reached, so asking again later
 * may find them.
 *
 * @param cause what the first failing source threw, the others are suppressed in it
 */
class LyricsException(cause: Throwable) : Exception("Couldn't reach every lyrics source", cause)

/**
 * Finds lyrics by asking [sources] one after another, in the order given. They are never asked at
 * the same time, some services limit how often a client may ask.
 */
class LyricsFinder(val sources: List<LyricsSource>) {

    /** @return the source of [provider], `null` when there is none */
    fun source(provider: LyricsProvider): LyricsSource? =
        sources.firstOrNull { it.provider == provider }

    /**
     * Stops at the first lyrics timed at least as finely as [wanted]. When no source has those, it
     * gives the most finely timed lyrics found, from the earliest source among equals. A source
     * that fails is skipped.
     *
     * @param exclude providers whose sources aren't asked, like one already asked
     * @return `null` when no source has lyrics for the song
     * @throws LyricsException when nothing was found and a source failed
     */
    suspend fun find(
        query: LyricsQuery,
        wanted: LyricsSync = LyricsSync.WORD,
        exclude: Set<LyricsProvider> = emptySet(),
    ): FoundLyrics? {
        var best: FoundLyrics? = null
        var failure: Throwable? = null

        for (source in sources) {
            if (source.provider in exclude) continue

            val found = try {
                source.find(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${source.provider} couldn't look up ${query.songId}", e)
                failure?.addSuppressed(e) ?: run { failure = e }
                null
            } ?: continue

            if (found.sync >= wanted) return found
            if (best == null || found.sync > best.sync) best = found
        }

        return best ?: failure?.let { throw LyricsException(it) }
    }
}
