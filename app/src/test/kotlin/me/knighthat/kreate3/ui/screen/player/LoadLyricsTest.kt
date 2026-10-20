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
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.lyrics.FoundLyrics
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.lyrics.LyricsQuery
import me.knighthat.kreate3.lyrics.LyricsSource
import me.knighthat.kreate3.lyrics.PlainLyrics
import me.knighthat.kreate3.lyrics.SyncedLyrics
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.screen.settings.prettyName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

// For android.util.Log
@RunWith(RobolectricTestRunner::class)
class LoadLyricsTest {

    private val query = LyricsQuery("s1", Origin.YOUTUBE, "Song")
    private val saved = mutableListOf<Lyrics>()

    // The provider the user chose, two others, and the one stored lyrics come from
    private val chosen = LyricsProvider.LRCLIB
    private val other = LyricsProvider.BETTER_LYRICS
    private val third = LyricsProvider.LYRICS_PLUS
    private val old = LyricsProvider.YOUTUBE

    /** Answers lyrics timed by [sync], none when it is `null`, or throws [error]. */
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
            return sync?.let { FoundLyrics("$provider lyrics", it, provider, "Credit") }
        }
    }

    private fun settings(
        source: LyricsProvider = chosen,
        sync: LyricsSync = LyricsSync.WORD,
        alwaysLive: Boolean = false,
    ) = LyricsSettings(source, sync, alwaysLive)

    private fun stored(sync: LyricsSync) = Lyrics("s1", "stored lyrics", sync, old, null)

    private fun load(
        finder: LyricsFinder,
        settings: LyricsSettings = settings(),
        everywhere: Boolean = false,
        stored: Lyrics? = null,
    ) = runBlocking {
        loadLyrics(query, settings, everywhere, finder, { stored }) { saved += it }
    }

    @Test
    fun storedLyricsTimedAsWantedAreShownWithoutAsking() {
        val chosenSource = FakeSource(chosen, LyricsSync.WORD)

        val state = load(LyricsFinder(listOf(chosenSource)), stored = stored(LyricsSync.WORD))

        assertEquals("stored lyrics", (state as LyricsState.Found).text)
        assertEquals(old.prettyName(), state.source)
        assertEquals(0, chosenSource.asked)
    }

    @Test
    fun storedLyricsTimedMoreFinelyThanWantedAskTheChosenSource() {
        val state = load(
            LyricsFinder(listOf(FakeSource(chosen, LyricsSync.LINE))),
            settings(sync = LyricsSync.LINE),
            stored = stored(LyricsSync.WORD),
        )

        assertEquals("$chosen lyrics", (state as LyricsState.Found).text)
        // Timed the way the user wants, so they replace the stored ones
        assertEquals(LyricsSync.LINE, saved.single().sync)
    }

    @Test
    fun storedLyricsTimedTooCoarselyAskTheChosenSource() {
        val state = load(
            LyricsFinder(
                listOf(
                    FakeSource(other, LyricsSync.WORD),
                    FakeSource(chosen, LyricsSync.LINE)
                )
            ),
            stored = stored(LyricsSync.NONE),
        )

        assertEquals("$chosen lyrics", (state as LyricsState.Found).text)
        assertEquals(chosen.name, state.source)
        assertEquals("Credit", state.credit)
        assertEquals(LyricsSync.LINE, saved.single().sync)
        assertEquals(chosen, saved.single().source)
    }

    @Test
    fun alwaysLiveNeitherReadsNorSaves() {
        val state = load(
            LyricsFinder(listOf(FakeSource(chosen, LyricsSync.NONE))),
            settings(sync = LyricsSync.NONE, alwaysLive = true),
            stored = stored(LyricsSync.WORD),
        )

        assertEquals("$chosen lyrics", (state as LyricsState.Found).text)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun onlyTheChosenSourceIsAskedFirst() {
        val otherSource = FakeSource(other, LyricsSync.WORD)

        val state = load(LyricsFinder(listOf(otherSource, FakeSource(chosen))))

        assertEquals(LyricsState.Unavailable(chosen.name), state)
        assertEquals(0, otherSource.asked)
    }

    @Test
    fun anUnknownChosenSourceIsTheFirstOne() {
        val state =
            load(LyricsFinder(listOf(FakeSource(other), FakeSource(chosen))), settings(old))

        assertEquals(LyricsState.Unavailable(other.name), state)
    }

    @Test
    fun lookingElsewhereGivesTheMostFinelyTimedOfTheOthers() {
        val chosenSource = FakeSource(chosen, LyricsSync.WORD)
        val finder = LyricsFinder(
            listOf(
                chosenSource,
                FakeSource(third, LyricsSync.NONE),
                FakeSource(other, LyricsSync.LINE)
            )
        )

        val state = load(finder, everywhere = true)

        assertEquals("$other lyrics", (state as LyricsState.Found).text)
        assertEquals(0, chosenSource.asked)
    }

    @Test
    fun lookingElsewhereKeepsStoredLyricsTimedMoreFinely() {
        val finder =
            LyricsFinder(listOf(FakeSource(chosen), FakeSource(third, LyricsSync.NONE)))

        val state = load(finder, everywhere = true, stored = stored(LyricsSync.LINE))

        assertEquals("$third lyrics", (state as LyricsState.Found).text)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun nothingElsewhereShowsTheStoredOnes() {
        val finder = LyricsFinder(listOf(FakeSource(chosen), FakeSource(other)))

        assertEquals(LyricsState.Missing, load(finder, everywhere = true))
        assertEquals(
            "stored lyrics",
            (load(
                finder,
                everywhere = true,
                stored = stored(LyricsSync.NONE)
            ) as LyricsState.Found).text,
        )
    }

    @Test
    fun shownLyricsAreReadAndTimedNoMoreFinelyThanWanted() {
        val lrc = "[00:01.00]<00:01.00>la <00:01.50>la"
        val source = object : LyricsSource {
            override val provider = chosen
            override val name = "Chosen"
            override suspend fun find(query: LyricsQuery) =
                FoundLyrics(lrc, LyricsSync.WORD, provider)
        }
        val finder = LyricsFinder(listOf(source))

        val words = load(finder) as LyricsState.Found
        val plain = load(finder, settings(sync = LyricsSync.NONE)) as LyricsState.Found

        assertEquals(LyricsSync.WORD, (words.content as SyncedLyrics).sync)
        assertEquals(PlainLyrics("la la"), plain.content)
        // What is stored is what the source gave
        assertEquals(lrc, saved.last().lyrics)
    }

    @Test
    fun aSourceThatCantBeReachedFails() {
        val down = LyricsFinder(listOf(FakeSource(chosen, error = IOException())))

        assertEquals(LyricsState.Failed, load(down))
        assertEquals(
            LyricsState.Failed,
            load(
                LyricsFinder(listOf(FakeSource(chosen), FakeSource(other, error = IOException()))),
                everywhere = true
            ),
        )
    }
}
