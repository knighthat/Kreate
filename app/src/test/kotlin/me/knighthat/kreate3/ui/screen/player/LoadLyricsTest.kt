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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

// For android.util.Log
@RunWith(RobolectricTestRunner::class)
class LoadLyricsTest {

    private val query = LyricsQuery("s1", Origin.YOUTUBE, "Song", listOf("Singer"), "Album")
    private val saved = mutableListOf<Lyrics>()

    // The provider the user chose, another one, and one without a source in the tests
    private val chosen = LyricsProvider.LRCLIB
    private val other = LyricsProvider.BETTER_LYRICS
    private val old = LyricsProvider.YOUTUBE

    /** Answers lyrics timed by [sync], none when it is `null`, or throws [error]. */
    private class FakeSource(
        override val provider: LyricsProvider,
        private val sync: LyricsSync? = null,
        private val error: Exception? = null,
    ) : LyricsSource {

        override val name: String = provider.name
        var asked = 0
        var lastQuery: LyricsQuery? = null

        override suspend fun find(query: LyricsQuery): FoundLyrics? {
            asked++
            lastQuery = query
            error?.let { throw it }
            return sync?.let { FoundLyrics("$provider lyrics", it, provider, "Credit") }
        }
    }

    private fun settings(
        source: LyricsProvider = chosen,
        sync: LyricsSync = LyricsSync.WORD,
        alwaysLive: Boolean = false,
    ) = LyricsSettings(source, sync, alwaysLive)

    /** Lyrics of [source] in the library, [sync] tells them apart from the fetched ones. */
    private fun stored(source: LyricsProvider, sync: LyricsSync = LyricsSync.NONE) =
        Lyrics("s1", "$source stored", sync, source, null)

    private fun load(
        finder: LyricsFinder,
        settings: LyricsSettings = settings(),
        request: LyricsRequest? = null,
        remote: Boolean = false,
        stored: List<Lyrics> = emptyList(),
    ) = runBlocking {
        loadLyrics(
            query = query,
            request = request,
            settings = settings,
            remote = remote,
            finder = finder,
            stored = { provider -> stored.firstOrNull { it.source == provider } },
        ) { saved += it }
    }

    @Test
    fun storedLyricsOfTheChosenSourceShowWithoutAskingWhateverTheirTiming() {
        val chosenSource = FakeSource(chosen, LyricsSync.WORD)

        val state = load(LyricsFinder(listOf(chosenSource)), stored = listOf(stored(chosen)))

        assertEquals("$chosen stored", (state as LyricsState.Found).text)
        assertEquals(chosen.name, state.source)
        assertEquals(0, chosenSource.asked)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun storedLyricsOfAnotherSourceDontShow() {
        val state = load(
            LyricsFinder(listOf(FakeSource(chosen, LyricsSync.LINE), FakeSource(other))),
            stored = listOf(stored(other, LyricsSync.WORD)),
        )

        assertEquals("$chosen lyrics", (state as LyricsState.Found).text)
        assertEquals(chosen.name, state.source)
        assertEquals("Credit", state.credit)
        assertEquals(chosen, saved.single().source)
        assertEquals(LyricsSync.LINE, saved.single().sync)
    }

    @Test
    fun aPickedSourceShowsItsStoredLyrics() {
        val chosenSource = FakeSource(chosen, LyricsSync.WORD)
        val otherSource = FakeSource(other, LyricsSync.WORD)

        val state = load(
            LyricsFinder(listOf(chosenSource, otherSource)),
            request = LyricsRequest(provider = other),
            stored = listOf(stored(chosen), stored(other)),
        )

        assertEquals("$other stored", (state as LyricsState.Found).text)
        assertEquals(0, chosenSource.asked)
        assertEquals(0, otherSource.asked)
    }

    @Test
    fun aPickedSourceWithoutStoredLyricsFetchesAndSavesThem() {
        val chosenSource = FakeSource(chosen, LyricsSync.WORD)
        val otherSource = FakeSource(other, LyricsSync.LINE)

        val state = load(
            LyricsFinder(listOf(chosenSource, otherSource)),
            request = LyricsRequest(provider = other),
            stored = listOf(stored(chosen)),
        )

        assertEquals("$other lyrics", (state as LyricsState.Found).text)
        assertEquals(0, chosenSource.asked)
        assertEquals(query, otherSource.lastQuery)
        // Coarser than wanted, but they are the source's
        assertEquals(other, saved.single().source)
        assertEquals(LyricsSync.LINE, saved.single().sync)
    }

    @Test
    fun aPickedSourceWithoutLyricsIsUnavailable() {
        val state = load(
            LyricsFinder(listOf(FakeSource(chosen, LyricsSync.WORD), FakeSource(other))),
            request = LyricsRequest(provider = other),
        )

        assertEquals(LyricsState.Unavailable(other.name), state)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun reloadingFetchesTheStoredOnesAgain() {
        val chosenSource = FakeSource(chosen, LyricsSync.WORD)

        val state = load(
            LyricsFinder(listOf(chosenSource)),
            remote = true,
            stored = listOf(stored(chosen)),
        )

        assertEquals("$chosen lyrics", (state as LyricsState.Found).text)
        assertEquals(1, chosenSource.asked)
        assertEquals("$chosen lyrics", saved.single().lyrics)
    }

    @Test
    fun alwaysLiveNeitherReadsNorSaves() {
        val state = load(
            LyricsFinder(listOf(FakeSource(chosen, LyricsSync.NONE))),
            settings(alwaysLive = true),
            stored = listOf(stored(chosen)),
        )

        assertEquals("$chosen lyrics", (state as LyricsState.Found).text)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun onlyTheChosenSourceIsAsked() {
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
    fun aSearchAsksEverySourceWithTheTypedWordsAndSkipsTheStoredOnes() {
        val lineSource = FakeSource(chosen, LyricsSync.LINE)
        val wordSource = FakeSource(other, LyricsSync.WORD)

        val state = load(
            LyricsFinder(listOf(lineSource, wordSource)),
            request = LyricsRequest(title = "Typed", artist = " "),
            stored = listOf(stored(chosen), stored(other)),
        )

        // Until one timed as the user wants
        assertEquals("$other lyrics", (state as LyricsState.Found).text)
        assertEquals(other, saved.single().source)
        val asked = wordSource.lastQuery!!
        assertEquals("Typed", asked.title)
        assertEquals(emptyList<String>(), asked.artists)
        assertEquals(null, asked.album)
        assertEquals(query.songId, asked.songId)
        assertEquals(query.duration, asked.duration)
    }

    @Test
    fun aSearchThatFindsNothingIsMissing() {
        assertEquals(
            LyricsState.Missing,
            load(
                LyricsFinder(listOf(FakeSource(chosen), FakeSource(other))),
                request = LyricsRequest(title = "Typed"),
            ),
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
        assertEquals(LyricsState.Failed, load(down, remote = true, stored = listOf(stored(chosen))))
        assertEquals(
            LyricsState.Failed,
            load(
                LyricsFinder(listOf(FakeSource(other, error = IOException()))),
                request = LyricsRequest(title = "Typed"),
            ),
        )
    }
}
