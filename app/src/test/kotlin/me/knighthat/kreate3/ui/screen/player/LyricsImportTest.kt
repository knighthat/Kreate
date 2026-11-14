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

package me.knighthat.kreate3.ui.screen.player

import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.model.LyricsSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.Locale

// For android.util.Log
@RunWith(RobolectricTestRunner::class)
class LyricsImportTest {

    private fun problemOf(block: () -> Unit) =
        assertThrows(LyricsFileException::class.java, block).problem

    private fun read(text: String) = runBlocking { readLyricsFile(text.toByteArray()) }

    @Test
    fun plainTextIsNotTimed() {
        val (text, sync) = read("\n\nFirst line\nSecond line\n\n")

        assertEquals("First line\nSecond line", text)
        assertEquals(LyricsSync.NONE, sync)
    }

    @Test
    fun lrcIsTimedPerLine() {
        assertEquals(
            LyricsSync.LINE,
            read("[ar:Someone]\r\n[00:01.00]First\r\n[00:02.00]Second").second
        )
    }

    @Test
    fun enhancedLrcIsTimedPerWord() {
        assertEquals(LyricsSync.WORD, read("[00:01.00]<00:01.00>la <00:01.50>la").second)
    }

    @Test
    fun ttmlIsTimedPerWord() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="1.0" end="2.0"><span begin="1.0" end="1.5">la </span><span begin="1.5" end="2.0">la</span></p>
            </div></body></tt>
        """.trimIndent()

        assertEquals(LyricsSync.WORD, read(ttml).second)
    }

    @Test
    fun aByteOrderMarkIsNotPartOfTheText() {
        val utf8 =
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01.00]la".toByteArray()
        val utf16 = byteArrayOf(
            0xFF.toByte(),
            0xFE.toByte()
        ) + "[00:01.00]la".toByteArray(Charsets.UTF_16LE)

        assertEquals("[00:01.00]la", decodeLyricsFile(utf8))
        assertEquals("[00:01.00]la", decodeLyricsFile(utf16))
        // Read after the mark, the first line keeps its time
        assertEquals(LyricsSync.LINE, runBlocking { readLyricsFile(utf8) }.second)
    }

    @Test
    fun textThatIsntUtf8IsReadInTheLegacyEncodingOfTheLanguage() {
        val chinese = "[00:01.00]月亮代表我的心"

        assertEquals(
            chinese,
            decodeLyricsFile(chinese.toByteArray(Charset.forName("GB18030")), Locale.CHINA)
        )
        assertEquals(
            "Café",
            decodeLyricsFile("Café".toByteArray(Charset.forName("windows-1252")), Locale.US)
        )
    }

    @Test
    fun aFileThatIsntTextIsRefused() {
        val picture = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            0,
            0,
            0,
            13
        )

        assertEquals(LyricsFileProblem.NOT_TEXT, problemOf { decodeLyricsFile(picture) })
    }

    @Test
    fun aFileWithoutWordsIsRefused() {
        assertEquals(LyricsFileProblem.EMPTY, problemOf { read("  \n[00:01.00]\n") })
    }

    @Test
    fun aFileLargerThanTheLimitIsRefused() {
        val large = ByteArray(MAX_LYRICS_FILE_BYTES + 1) { 'a'.code.toByte() }

        assertEquals(
            LyricsFileProblem.TOO_LARGE,
            problemOf { readBounded(ByteArrayInputStream(large)) })
        assertEquals(
            MAX_LYRICS_FILE_BYTES,
            readBounded(ByteArrayInputStream(large.copyOf(MAX_LYRICS_FILE_BYTES))).size
        )
    }
}
