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

import me.knighthat.kreate3.database.model.LyricsSync
import kotlin.time.Duration

/** The lyrics of a song, read from the text a source gave, see [parseLyrics]. */
sealed interface LyricsContent {

    /** How finely the lyrics are timed. */
    val sync: LyricsSync

    /** The words sung, one line per line, without timing or background vocals. */
    val text: String
}

/** Lyrics without timing. */
data class PlainLyrics(override val text: String) : LyricsContent {

    override val sync: LyricsSync
        get() = LyricsSync.NONE
}

/**
 * Lyrics timed per line, and per word when lines have [LyricsLine.words].
 *
 * @property lines by the time they start; lines may overlap, like background vocals sung over the
 * next line
 */
data class SyncedLyrics(val lines: List<LyricsLine>) : LyricsContent {

    override val sync: LyricsSync
        get() = if (lines.any { it.words.isNotEmpty() }) LyricsSync.WORD else LyricsSync.LINE

    override val text: String
        get() = lines.filterNot { it.background }.joinToString("\n") { it.text }
}

/**
 * A line of [SyncedLyrics].
 *
 * @property start from the start of the song
 * @property end from the start of the song, `null` when it lasts until the next line
 * @property text the whole line, empty for a pause between lines
 * @property words the line split into its timed words or syllables, which join back into [text]
 * (each keeps the space after it, syllables of a word have none); empty when only the line is
 * timed
 * @property background sung by backing vocals over the main line, shown smaller
 */
data class LyricsLine(
    val start: Duration,
    val end: Duration?,
    val text: String,
    val words: List<LyricsWord> = emptyList(),
    val background: Boolean = false,
)

/**
 * A timed word or syllable of a [LyricsLine].
 *
 * @property start from the start of the song
 * @property end from the start of the song, `null` when it lasts until the next word
 * @property text the word, with the space that follows it
 */
data class LyricsWord(
    val start: Duration,
    val end: Duration?,
    val text: String,
)

/**
 * The lyrics timed no more finely than [sync]: word timing dropped for [LyricsSync.LINE], every
 * timing for [LyricsSync.NONE]. Lyrics already timed that coarsely are given back as they are.
 */
fun LyricsContent.coarsenedTo(sync: LyricsSync): LyricsContent = when {
    this.sync <= sync -> this
    sync == LyricsSync.NONE -> PlainLyrics(text)
    this is SyncedLyrics -> SyncedLyrics(lines.map { it.copy(words = emptyList()) })
    else -> this
}
