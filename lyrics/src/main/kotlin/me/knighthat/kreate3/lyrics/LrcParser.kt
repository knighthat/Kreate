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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A line's time, `[01:02.34]`; the fraction may have 1 to 3 digits, or be missing. */
private val LINE_TIME = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** A word's time in enhanced LRC, `<01:02.34>`. */
private val WORD_TIME = Regex("""<(\d+):(\d{1,2})(?:[.:](\d{1,3}))?>""")

private val SPACES = Regex("""\s+""")

/** `[offset:+500]`, milliseconds to show the lyrics earlier by, later when negative. */
private val OFFSET = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)

/** "5", "50" and "500" all are half a second. */
private fun MatchResult.time(): Duration {
    val (minutes, seconds, fraction) = destructured
    val millis = fraction.padEnd(3, '0').take(3).toInt()
    return minutes.toInt().minutes + seconds.toInt().seconds + millis.milliseconds
}

/**
 * Reads LRC, timed per line, and enhanced LRC, also timed per word:
 *
 * ```
 * [00:12.00]Is this the real life?
 * [00:12.00]<00:12.00>Is <00:12.40>this <00:12.80>the <00:13.20>real <00:13.90>life?<00:14.60>
 * ```
 *
 * Tags like `[ar:…]` are skipped, a line with several times is sung at each of them.
 *
 * @return `null` when no line is timed, the text is then plain lyrics
 */
internal fun parseLrc(text: String): SyncedLyrics? {
    var offset = Duration.ZERO
    val lines = mutableListOf<LyricsLine>()

    for (raw in text.lineSequence()) {
        OFFSET.find(raw)?.let {
            offset = it.groupValues[1].toInt().milliseconds
            continue
        }

        var rest = raw.trim()
        val starts = mutableListOf<Duration>()
        while (true) {
            val time = LINE_TIME.matchAt(rest, 0) ?: break
            starts += time.time()
            rest = rest.substring(time.range.last + 1)
        }
        if (starts.isEmpty()) continue

        for (start in starts) {
            val words = words(rest, start)
            val lineText = if (words.isEmpty())
                WORD_TIME.replace(rest, "").trim()
            else
                words.joinToString("") { it.text }.trim()
            lines += LyricsLine(start, null, lineText, words)
        }
    }
    if (lines.isEmpty()) return null

    val shifted = if (offset == Duration.ZERO) lines else lines.map { it.shiftedBy(-offset) }
    return SyncedLyrics(shifted.withEnds())
}

/** @return empty when the line has no word times */
private fun words(rest: String, lineStart: Duration): List<LyricsWord> {
    val tags = WORD_TIME.findAll(rest).toList()
    if (tags.isEmpty()) return emptyList()

    val words = mutableListOf<LyricsWord>()
    // Some files leave out the time of the first word, it starts with the line
    rest.substring(0, tags.first().range.first).trimStart().takeIf { it.isNotBlank() }
        ?.let { words += LyricsWord(lineStart, tags.first().time(), it) }

    tags.forEachIndexed { i, tag ->
        val next = tags.getOrNull(i + 1)
        val word = rest.substring(tag.range.last + 1, next?.range?.first ?: rest.length)
        // An empty word is a closing time, the end of the word before it
        if (word.isNotEmpty())
            words += LyricsWord(tag.time(), next?.time(), word.replace(SPACES, " "))
    }
    return words.mapIndexed { i, word -> if (i == 0) word.copy(text = word.text.trimStart()) else word }
        .filter { it.text.isNotEmpty() }
}

private fun LyricsLine.shiftedBy(by: Duration): LyricsLine {
    fun Duration.shifted() = (this + by).coerceAtLeast(Duration.ZERO)
    return copy(
        start = start.shifted(),
        end = end?.shifted(),
        words = words.map { it.copy(start = it.start.shifted(), end = it.end?.shifted()) },
    )
}
