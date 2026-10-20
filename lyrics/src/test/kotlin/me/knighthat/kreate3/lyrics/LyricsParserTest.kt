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

import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.model.LyricsSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LyricsParserTest {

    private fun parse(text: String) = runBlocking { parseLyrics(text) }

    private fun synced(text: String) = parse(text) as SyncedLyrics

    // <editor-fold desc="LRC">
    @Test
    fun lrcLinesEndWhereTheNextStarts() {
        val lyrics = synced(
            """
            [ar:Someone]
            [00:01.00]First line
            [00:03.5]Second line
            [00:05.250]
            """.trimIndent()
        )

        assertEquals(LyricsSync.LINE, lyrics.sync)
        assertEquals(listOf("First line", "Second line", ""), lyrics.lines.map { it.text })
        assertEquals(listOf(1.seconds, 3.5.seconds, 5.25.seconds), lyrics.lines.map { it.start })
        assertEquals(listOf(3.5.seconds, 5.25.seconds, null), lyrics.lines.map { it.end })
    }

    @Test
    fun lrcLineWithSeveralTimesIsSungAtEach() {
        val lyrics = synced("[00:10.00]Verse\n[00:05.00][00:20.00]Chorus")

        assertEquals(listOf("Chorus", "Verse", "Chorus"), lyrics.lines.map { it.text })
        assertEquals(listOf(5.seconds, 10.seconds, 20.seconds), lyrics.lines.map { it.start })
    }

    @Test
    fun enhancedLrcHasItsWords() {
        val lyrics = synced("[00:12.00]<00:12.00>Is <00:12.40>this <00:12.80>real?<00:13.50>")
        val line = lyrics.lines.single()

        assertEquals(LyricsSync.WORD, lyrics.sync)
        assertEquals("Is this real?", line.text)
        assertEquals(listOf("Is ", "this ", "real?"), line.words.map { it.text })
        assertEquals(listOf(12.seconds, 12.4.seconds, 12.8.seconds), line.words.map { it.start })
        assertEquals(listOf(12.4.seconds, 12.8.seconds, 13.5.seconds), line.words.map { it.end })
        // The last word's end is the line's
        assertEquals(13.5.seconds, line.end)
    }

    @Test
    fun enhancedLrcWithoutTheFirstWordTimeStartsItWithTheLine() {
        val line = synced("[00:01.00]Is <00:01.50>this").lines.single()

        assertEquals(listOf("Is ", "this"), line.words.map { it.text })
        assertEquals(listOf(1.seconds, 1.5.seconds), line.words.map { it.start })
    }

    @Test
    fun lrcOffsetShowsTheLyricsEarlier() {
        val lyrics = synced("[offset:+500]\n[00:02.00]<00:02.00>la <00:03.00>la")

        assertEquals(1.5.seconds, lyrics.lines.single().start)
        assertEquals(listOf(1.5.seconds, 2.5.seconds), lyrics.lines.single().words.map { it.start })
    }

    @Test
    fun textWithoutTimesIsPlain() {
        val text = "[Verse 1]\nSome words\nMore words"

        assertEquals(PlainLyrics(text), parse(text))
    }
    // </editor-fold>

    // <editor-fold desc="TTML">
    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
            xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word">
          <head><metadata><ttm:agent type="person" xml:id="v1"><ttm:name type="full">Vocal 1</ttm:name></ttm:agent></metadata></head>
          <body dur="1:00.000">
            <div begin="1.000" end="9.000">
              <p begin="1.000" end="3.000" ttm:agent="v1"><span begin="1.000" end="1.400">Beau</span><span begin="1.400" end="2.000">tiful</span> <span begin="2.000" end="3.000">day</span><span ttm:role="x-bg"><span begin="2.500" end="2.800">(oh</span> <span begin="2.800" end="3.200">yeah)</span></span></p>
              <p begin="1:04.000" end="1:05.500"><span begin="1:04.000" end="1:05.500">Again</span></p>
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun ttmlLinesHaveTheirSyllables() {
        val lyrics = synced(ttml)
        val first = lyrics.lines.first()

        assertEquals(LyricsSync.WORD, lyrics.sync)
        assertEquals("Beautiful day", first.text)
        // Syllables of a word have no space between them
        assertEquals(listOf("Beau", "tiful ", "day"), first.words.map { it.text })
        assertEquals(listOf(1.seconds, 1.4.seconds, 2.seconds), first.words.map { it.start })
        assertEquals(3.seconds, first.end)
        assertEquals(64.seconds, lyrics.lines.last().start)
        assertEquals("Beautiful day\nAgain", lyrics.text)
    }

    @Test
    fun ttmlBackingVocalsAreALineOfTheirOwn() {
        val backing = synced(ttml).lines.single { it.background }

        assertEquals("(oh yeah)", backing.text)
        assertEquals(listOf("(oh ", "yeah)"), backing.words.map { it.text })
        assertEquals(2.5.seconds, backing.start)
        assertEquals(3.2.seconds, backing.end)
    }

    @Test
    fun ttmlTimedPerLineHasNoWords() {
        val lyrics = synced(
            """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Line"><body><div>""" +
                    """<p begin="00:00:01.000" end="00:00:02.000">One  line</p><p begin="2s" end="3000ms">Two</p></div></body></tt>"""
        )

        assertEquals(LyricsSync.LINE, lyrics.sync)
        assertEquals(listOf("One line", "Two"), lyrics.lines.map { it.text })
        assertEquals(listOf(1.seconds, 2.seconds), lyrics.lines.map { it.start })
        assertEquals(3.seconds, lyrics.lines.last().end)
    }

    @Test
    fun ttmlNotTimedIsPlain() {
        val lyrics = parse(
            """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="None"><body><div>""" +
                    """<p begin="0" end="1">One</p><p begin="0" end="1">Two</p></div></body></tt>"""
        )

        assertEquals(PlainLyrics("One\nTwo"), lyrics)
    }

    @Test
    fun brokenTtmlIsShownAsItIs() {
        val text = "<tt><body><p begin=\"1\">unclosed"

        assertEquals(PlainLyrics(text), parse(text))
    }
    // </editor-fold>

    // <editor-fold desc="LyricsPlus">
    @Test
    fun lyricsPlusLinesHaveTheirSyllablesAndBackingVocals() {
        val lyrics = synced(
            """{"type":"Word","metadata":{"source":"Apple"},"lyrics":[
              {"time":1000,"duration":2000,"text":"Is this","syllabus":[
                {"time":1000,"duration":500,"text":"Is "},
                {"time":1500,"duration":1500,"text":"this"},
                {"time":2000,"duration":800,"text":"(real)","isBackground":true}]},
              {"time":4000,"duration":1000,"text":"End","syllabus":[{"time":4000,"duration":1000,"text":"End"}]}]}"""
        )

        assertEquals(LyricsSync.WORD, lyrics.sync)
        assertEquals(listOf("Is this", "(real)", "End"), lyrics.lines.map { it.text })
        assertEquals(listOf(false, true, false), lyrics.lines.map { it.background })
        assertEquals(listOf(1.seconds, 1.5.seconds), lyrics.lines.first().words.map { it.start })
        assertEquals(3.seconds, lyrics.lines.first().end)
        assertEquals(2800.milliseconds, lyrics.lines[1].end)
    }

    @Test
    fun lyricsPlusTimedPerLineHasNoWords() {
        val lyrics = synced(
            """{"type":"Line","lyrics":[{"time":0,"duration":1000,"text":"One"},{"time":1000,"duration":1000,"text":"Two"}]}"""
        )

        assertEquals(LyricsSync.LINE, lyrics.sync)
        assertEquals(listOf("One", "Two"), lyrics.lines.map { it.text })
    }
    // </editor-fold>

    @Test
    fun clockTimesAreRead() {
        assertEquals(27395.milliseconds, parseClock("27.395"))
        assertEquals(62345.milliseconds, parseClock("1:02.345"))
        assertEquals(3723456.milliseconds, parseClock("01:02:03.456"))
        assertEquals(1.5.seconds, parseClock("1.5s"))
        assertEquals(250.milliseconds, parseClock("250ms"))
        assertNull(parseClock("soon"))
    }

    @Test
    fun lyricsAreCoarsenedToTheTimingWanted() {
        val words = synced("[00:01.00]<00:01.00>la <00:01.50>la")

        val lines = words.coarsenedTo(LyricsSync.LINE) as SyncedLyrics
        assertTrue(lines.lines.single().words.isEmpty())
        assertEquals("la la", lines.lines.single().text)
        assertEquals(PlainLyrics("la la"), words.coarsenedTo(LyricsSync.NONE))
        assertEquals(words, words.coarsenedTo(LyricsSync.WORD))
        assertEquals(lines, lines.coarsenedTo(LyricsSync.WORD))
    }
}
