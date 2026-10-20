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
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsProvider.BETTER_LYRICS
import me.knighthat.kreate3.model.LyricsProvider.LRCLIB
import me.knighthat.kreate3.model.LyricsProvider.LYRICS_PLUS
import me.knighthat.kreate3.model.LyricsProvider.YOUTUBE
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.model.Origin
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
        override val provider: LyricsProvider,
        private val sync: LyricsSync? = null,
        private val error: Exception? = null,
    ) : LyricsSource {

        override val name: String = provider.name
        var asked = 0

        override suspend fun find(query: LyricsQuery): FoundLyrics? {
            asked++
            error?.let { throw it }
            return sync?.let { FoundLyrics("la la", it, provider) }
        }
    }

    @Test
    fun stopsAtTheFirstWordSyncedLyrics() = runBlocking {
        val later = FakeSource(LYRICS_PLUS, LyricsSync.WORD)
        val finder = LyricsFinder(
            listOf(
                FakeSource(YOUTUBE, LyricsSync.NONE),
                FakeSource(BETTER_LYRICS, LyricsSync.WORD),
                later,
            )
        )

        val found = finder.find(query)

        assertEquals(BETTER_LYRICS, found?.source)
        assertEquals(0, later.asked)
    }

    @Test
    fun givesTheMostFinelyTimedWhenNoneHasWords() = runBlocking {
        val finder = LyricsFinder(
            listOf(
                FakeSource(YOUTUBE, LyricsSync.NONE),
                FakeSource(LRCLIB, LyricsSync.LINE),
                FakeSource(LYRICS_PLUS, LyricsSync.LINE),
            )
        )

        assertEquals(LRCLIB, finder.find(query)?.source)
    }

    @Test
    fun stopsAtWhatIsWanted() = runBlocking {
        val later = FakeSource(BETTER_LYRICS, LyricsSync.WORD)
        val finder = LyricsFinder(listOf(FakeSource(YOUTUBE, LyricsSync.NONE), later))

        assertEquals(YOUTUBE, finder.find(query, LyricsSync.NONE)?.source)
        assertEquals(0, later.asked)
    }

    @Test
    fun excludedSourcesAreNotAsked() = runBlocking {
        val excluded = FakeSource(BETTER_LYRICS, LyricsSync.WORD)
        val finder = LyricsFinder(listOf(excluded, FakeSource(LRCLIB, LyricsSync.LINE)))

        assertEquals(LRCLIB, finder.find(query, exclude = setOf(BETTER_LYRICS))?.source)
        assertEquals(0, excluded.asked)
    }

    @Test
    fun skipsASourceThatFails() = runBlocking {
        val finder = LyricsFinder(
            listOf(
                FakeSource(BETTER_LYRICS, error = IOException()),
                FakeSource(LRCLIB, LyricsSync.LINE),
            )
        )

        assertEquals(LRCLIB, finder.find(query)?.source)
    }

    @Test
    fun nothingFoundIsNull() = runBlocking {
        assertNull(LyricsFinder(listOf(FakeSource(LRCLIB), FakeSource(YOUTUBE))).find(query))
        assertNull(LyricsFinder(emptyList()).find(query))
    }

    @Test
    fun nothingFoundWithAFailureThrows() = runBlocking {
        val first = IOException("first")
        val second = IOException("second")
        val finder = LyricsFinder(
            listOf(
                FakeSource(BETTER_LYRICS, error = first),
                FakeSource(LYRICS_PLUS),
                FakeSource(LRCLIB, error = second),
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
    fun findsASourceByItsProvider() {
        val source = FakeSource(LRCLIB)
        val finder = LyricsFinder(listOf(source))

        assertSame(source, finder.source(LRCLIB))
        assertNull(finder.source(YOUTUBE))
    }
}
