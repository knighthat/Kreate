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
import me.knighthat.kreate3.database.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class LyricsFinderTest {

    private val query = LyricsQuery("song", Origin.YOUTUBE, "Title", listOf("Artist"))

    /** Answers lyrics timed by [sync], or throws [error]; counts how often it was asked. */
    private class FakeSource(
        override val id: String,
        private val sync: LyricsSync? = null,
        private val error: Exception? = null,
    ) : LyricsSource {

        override val name: String = id
        var asked = 0

        override suspend fun find(query: LyricsQuery): FoundLyrics? {
            asked++
            error?.let { throw it }
            return sync?.let { FoundLyrics("la la", it, id) }
        }
    }

    @Test
    fun stopsAtTheFirstWordSyncedLyrics() = runBlocking {
        val later = FakeSource("later", LyricsSync.WORD)
        val finder = LyricsFinder(
            listOf(
                FakeSource("plain", LyricsSync.NONE),
                FakeSource("words", LyricsSync.WORD),
                later
            )
        )

        val found = finder.find(query)

        assertEquals("words", found?.source)
        assertEquals(0, later.asked)
    }

    @Test
    fun givesTheMostFinelyTimedWhenNoneHasWords() = runBlocking {
        val finder = LyricsFinder(
            listOf(
                FakeSource("plain", LyricsSync.NONE),
                FakeSource("line1", LyricsSync.LINE),
                FakeSource("line2", LyricsSync.LINE),
            )
        )

        assertEquals("line1", finder.find(query)?.source)
    }

    @Test
    fun stopsAtWhatIsWanted() = runBlocking {
        val later = FakeSource("words", LyricsSync.WORD)
        val finder = LyricsFinder(listOf(FakeSource("plain", LyricsSync.NONE), later))

        assertEquals("plain", finder.find(query, LyricsSync.NONE)?.source)
        assertEquals(0, later.asked)
    }

    @Test
    fun excludedSourcesAreNotAsked() = runBlocking {
        val excluded = FakeSource("words", LyricsSync.WORD)
        val finder = LyricsFinder(listOf(excluded, FakeSource("line", LyricsSync.LINE)))

        assertEquals("line", finder.find(query, exclude = setOf("words"))?.source)
        assertEquals(0, excluded.asked)
    }

    @Test
    fun skipsASourceThatFails() = runBlocking {
        val finder = LyricsFinder(
            listOf(FakeSource("down", error = IOException()), FakeSource("line", LyricsSync.LINE))
        )

        assertEquals("line", finder.find(query)?.source)
    }

    @Test
    fun nothingFoundIsNull() = runBlocking {
        assertNull(LyricsFinder(listOf(FakeSource("a"), FakeSource("b"))).find(query))
        assertNull(LyricsFinder(emptyList()).find(query))
    }

    @Test
    fun nothingFoundWithAFailureThrows() = runBlocking {
        val first = IOException("first")
        val second = IOException("second")
        val finder = LyricsFinder(
            listOf(
                FakeSource("a", error = first),
                FakeSource("b"),
                FakeSource("c", error = second),
            )
        )

        try {
            finder.find(query)
            fail("Expected a LyricsException")
        } catch (e: LyricsException) {
            assertSame(first, e.cause)
            assertTrue(second in first.suppressed)
        }
        Unit
    }

    @Test
    fun findsASourceById() {
        val source = FakeSource("lrclib")
        val finder = LyricsFinder(listOf(source))

        assertSame(source, finder.source("lrclib"))
        assertNull(finder.source("missing"))
    }
}
