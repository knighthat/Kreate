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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "LyricsParser"

/**
 * Reads lyrics as a source gave them, [FoundLyrics.text] or stored ones. The format is told
 * from the text itself, not from the source, which may change what it sends:
 *
 * - TTML, like Better Lyrics', starts with `<`;
 * - the JSON of LyricsPlus starts with `{`;
 * - LRC and enhanced LRC have lines starting with a time like `[01:02.34]`;
 * - anything else is plain text.
 *
 * Text that can't be read is given back as [PlainLyrics]. Main-safe: it parses off the caller's
 * thread.
 */
suspend fun parseLyrics(text: String): LyricsContent = withContext(Dispatchers.Default) {
    val trimmed = text.trim()
    try {
        when {
            trimmed.startsWith("<") -> parseTtml(trimmed)
            trimmed.startsWith("{") -> parseLyricsPlus(trimmed)
            else -> parseLrc(trimmed)
        } ?: PlainLyrics(trimmed)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the lyrics, they are shown as they are", e)
        PlainLyrics(trimmed)
    }
}

/**
 * A time like `27.395`, `1:02.345` or `01:02:03.456`, and the TTML offsets `27.395s` and
 * `27395ms`.
 *
 * @return `null` when it isn't one
 */
internal fun parseClock(value: String): Duration? {
    val clock = value.trim()
    if (clock.endsWith("ms")) return clock.dropLast(2).toDoubleOrNull()?.milliseconds
    if (clock.endsWith("s")) return clock.dropLast(1).toDoubleOrNull()?.let { secondsOf(it) }

    val parts = clock.split(':')
    if (parts.size > 3) return null
    val seconds =
        parts.fold(0.0) { total, part -> total * 60 + (part.toDoubleOrNull() ?: return null) }
    return secondsOf(seconds)
}

private fun secondsOf(seconds: Double): Duration? =
    seconds.takeIf { it >= 0 }?.let { (it * 1000).roundToLong().milliseconds }

/** Each line ends where the next line that isn't a background one starts, unless it has an end. */
internal fun List<LyricsLine>.withEnds(): List<LyricsLine> {
    val sorted = sortedBy { it.start }
    return sorted.mapIndexed { i, line ->
        if (line.end != null) return@mapIndexed line
        val next = sorted.subList(i + 1, sorted.size).firstOrNull { !it.background }
        line.copy(end = line.words.lastOrNull()?.end ?: next?.start)
    }
}
