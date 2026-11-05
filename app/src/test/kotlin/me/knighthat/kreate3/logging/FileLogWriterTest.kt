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

package me.knighthat.kreate3.logging

import co.touchlab.kermit.Severity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class FileLogWriterTest {

    private lateinit var directory: File
    private lateinit var writer: FileLogWriter

    @Before
    fun setUp() {
        directory = File.createTempFile("logs", "").apply {
            delete()
            mkdirs()
        }
        writer = FileLogWriter(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun log(severity: Severity, text: String, throwable: Throwable? = null) =
        writer.log(severity, text, "Test", throwable)

    private fun current() = LogFiles.current(directory).takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun messagesWaitForTheLevelThenAreFiltered() {
        log(Severity.Debug, "early debug")
        log(Severity.Error, "early error")
        writer.flush(2_000)
        assertEquals("nothing is written before the level is known", "", current())

        writer.configure(Severity.Warn, 1024 * 1024, 5)
        writer.flush(2_000)

        assertFalse("early debug" in current())
        assertTrue("E/Test" in current())
        assertTrue("early error" in current())
    }

    @Test
    fun belowTheLevelIsDropped() {
        writer.configure(Severity.Warn, 1024 * 1024, 5)
        log(Severity.Info, "quiet")
        log(Severity.Warn, "loud")
        writer.flush(2_000)

        assertFalse("quiet" in current())
        assertTrue("loud" in current())
    }

    @Test
    fun secretsAndStackTracesAreWritten() {
        writer.configure(Severity.Verbose, 1024 * 1024, 5)
        log(Severity.Warn, "GET https://x.example/a?token=abc", IllegalStateException("boom"))
        writer.flush(2_000)

        assertFalse("abc" in current())
        assertTrue("token=<redacted>" in current())
        assertTrue("IllegalStateException: boom" in current())
    }

    @Test
    fun theFileRollsOverAndOldOnesPastKeepGo() {
        // The writer raises anything below 16 KiB to that, so the lines are long
        writer.configure(Severity.Verbose, 16 * 1024, 2)
        val line = "x".repeat(1_000)
        repeat(100) { log(Severity.Info, "$it $line") }
        writer.flush(5_000)

        assertTrue(LogFiles.current(directory).exists())
        assertTrue(LogFiles.rolled(directory, 1).exists())
        assertTrue(LogFiles.rolled(directory, 2).exists())
        assertFalse("only 2 rolled files are kept", LogFiles.rolled(directory, 3).exists())
        assertTrue(LogFiles.current(directory).length() <= 17 * 1024)
        // The newest message is in the current file
        assertTrue("99 " in current())
    }

    @Test
    fun theLogsGoInOneZip() {
        writer.configure(Severity.Verbose, 1024 * 1024, 5)
        log(Severity.Info, "hello")
        writer.flush(2_000)

        val bytes = ByteArrayOutputStream()
        assertEquals(1, LogFiles.zip(directory, bytes))

        val names = ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(listOf(LogFiles.CURRENT_NAME), names)
    }

    @Test
    fun tailStartsOnALine() {
        val file = File(directory, "kreate.log")
        file.writeText((1..100).joinToString("\n") { "line $it" })

        val tail = LogFiles.tail(file, limit = 50)

        assertTrue(tail.startsWith("line "))
        assertTrue(tail.endsWith("line 100"))
        assertTrue(tail.length <= 50)
    }

    @Test
    fun onlyTheEntriesOfTheSessionAreKept() {
        val format = java.text.SimpleDateFormat(LogFiles.TIME_FORMAT, java.util.Locale.ROOT)
        fun at(millis: Long) = format.format(java.util.Date(millis))
        File(directory, "kreate.log").writeText(
            """
            ${at(1_000_000)} E/Test [main]: before the session
            java.lang.IllegalStateException: old
            	at old.Trace(Old.kt:1)
            ${at(2_000_000)} W/Test [main]: in the session
            java.lang.IllegalStateException: new
            	at new.Trace(New.kt:1)
            """.trimIndent()
        )

        val text = LogFiles.latestSince(directory, sinceMillis = 1_500_000)

        assertFalse("before the session" in text)
        assertFalse("old.Trace" in text)
        assertTrue("in the session" in text)
        assertTrue("new.Trace" in text)
    }

    @Test
    fun recentActivityKeepsInfoThatTheLevelDropsButNotTheFile() {
        writer.configure(Severity.Warn, 1024 * 1024, 5)
        log(Severity.Debug, "too detailed")
        log(Severity.Info, "song changed")
        writer.flush(2_000)

        val recent = writer.recent()
        assertTrue("song changed" in recent.text)
        assertFalse("too detailed" in recent.text)
        assertFalse("song changed" in current())
    }

    @Test
    fun recentActivityKeepsTheLatestEntriesOnly() {
        writer.configure(Severity.Warn, 1024 * 1024, 5)
        repeat(RECENT_ENTRIES + 50) { log(Severity.Info, "entry $it") }

        val lines = writer.recent().text.lines().filter { it.isNotEmpty() }
        assertEquals(RECENT_ENTRIES, lines.size)
        assertFalse("entry 0\n" in writer.recent().text)
        assertTrue("entry ${RECENT_ENTRIES + 49}" in lines.last())
    }

    @Test
    fun entriesFromAnEarlierLogAreLeftOut() {
        val format = java.text.SimpleDateFormat(LogFiles.TIME_FORMAT, java.util.Locale.ROOT)
        fun at(millis: Long) = format.format(java.util.Date(millis))
        File(directory, "kreate.log").writeText(
            "${at(1_000)} W/Test [main]: first\n${at(2_000)} W/Test [main]: second\n"
        )

        val text = LogFiles.latestSince(directory, sinceMillis = 0, untilMillis = 1_500)

        assertTrue("first" in text)
        assertFalse("second" in text)
    }
}
