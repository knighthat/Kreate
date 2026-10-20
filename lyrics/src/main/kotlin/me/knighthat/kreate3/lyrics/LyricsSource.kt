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

import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync

/**
 * Somewhere lyrics can be found, like a lyrics service or the song's own origin. Ask them all
 * through [LyricsFinder].
 *
 * Like providers, [find] is main-safe: it reads and parses the answer off the caller's thread.
 */
interface LyricsSource {

    /** Which provider this source fetches from, each has at most one source. */
    val provider: LyricsProvider

    /** Shown to the user, like "LRCLIB". */
    val name: String

    /**
     * @return `null` when the source has no lyrics for the song, or can't look up songs like it
     * @throws Exception when the source can't be reached or its answer can't be read
     */
    suspend fun find(query: LyricsQuery): FoundLyrics?
}

/**
 * Lyrics a [LyricsSource] found.
 *
 * @property text the lyrics as the source gave them: plain text, LRC, TTML or the source's JSON
 * @property sync how finely [text] is timed
 * @property source the provider of the source that found them
 * @property credit who the source names as the provider of the lyrics, as it words it, like
 * "Source: Musixmatch"; show it with the lyrics. `null` when it names no one
 */
data class FoundLyrics(
    val text: String,
    val sync: LyricsSync,
    val source: LyricsProvider,
    val credit: String? = null,
)
