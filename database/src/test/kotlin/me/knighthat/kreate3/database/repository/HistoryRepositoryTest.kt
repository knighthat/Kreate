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

package me.knighthat.kreate3.database.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.DatabaseTest
import me.knighthat.kreate3.database.asList
import me.knighthat.kreate3.database.entity.SearchHistoryEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.paging.SearchSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class HistoryRepositoryTest : DatabaseTest() {

    private lateinit var repository: HistoryRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        addUser()
        addUser("other")
        repository = HistoryRepositoryImpl(
            database,
            database.searchHistoryDao(),
            database.playbackHistoryDao(),
            activeUser,
            clock,
        )
    }

    private suspend fun search(query: String) {
        repository.recordSearch(query)
        clock.advance()
    }

    private suspend fun suggestions(prefix: String, limit: Int = 10) =
        repository.searchesStartingWith(prefix, limit).first().map { it.query }

    @Test
    fun searchingAgainMovesTheSearchUp() = runBlocking {
        search("queen")
        search("abba")
        val again = clock.now()
        search("queen")

        val searches = repository.searches(SortOrder(SearchSort.QUERY)).asList()
        assertEquals(listOf("abba", "queen"), searches.map { it.query })
        assertEquals(again, searches.last().lastUsed)
        assertEquals(listOf("queen", "abba"), suggestions(""))
    }

    @Test
    fun searchingWithSpacesAroundKeepsOneSearch() = runBlocking {
        search("queen")
        search("abba")
        search("  queen ")
        search("\tqueen\n")
        // "Sơn" with a combining horn, then precomposed
        search("Sơn Tùng")
        search("Sơn Tùng")

        assertEquals(listOf("Sơn Tùng", "queen", "abba"), suggestions(""))
    }

    @Test
    fun searchesInAnotherCaseOrInnerSpacingAreKeptApart() = runBlocking {
        search("queen")
        search("Queen")
        search("QUEEN ")
        search("the queen")
        search("the  queen")

        assertEquals(
            listOf("the  queen", "the queen", "QUEEN", "Queen", "queen"),
            suggestions(""),
        )
    }

    @Test
    fun searchingReplacesASearchSavedUntrimmed() = runBlocking {
        database.searchHistoryDao().upsert(SearchHistoryEntity(" the queen ", USER, clock.now()))
        database.searchHistoryDao().upsert(SearchHistoryEntity(" The Queen", USER, clock.now()))
        database.searchHistoryDao().upsert(SearchHistoryEntity("the  queen ", USER, clock.now()))
        clock.advance()
        search("the queen")

        assertEquals(listOf("the queen", " The Queen", "the  queen "), suggestions(""))
    }

    @Test
    fun suggestionsStartWithWhatIsTyped() = runBlocking {
        search("queen")
        search("Quiet riot")
        search("the queen")

        assertEquals(listOf("Quiet riot", "queen"), suggestions("qu"))
    }

    @Test
    fun suggestionsTakeWildcardsLiterally() = runBlocking {
        search("a_b")
        search("axb")
        search("50% off")
        search("500 miles")

        assertEquals(listOf("a_b"), suggestions("a_"))
        assertEquals(listOf("50% off"), suggestions("50%"))
    }

    @Test
    fun suggestionsAreLimited() = runBlocking {
        (1..5).forEach { search("song $it") }

        assertEquals(listOf("song 5", "song 4"), suggestions("song", limit = 2))
    }

    @Test
    fun searchesBelongToTheirUser() = runBlocking {
        search("mine")
        activeUser.id.value = "other"
        search("theirs")

        assertEquals(listOf("theirs"), suggestions(""))

        repository.clearSearches()
        activeUser.id.value = USER
        assertEquals(listOf("mine"), suggestions(""))
    }

    @Test
    fun aSearchIsDeleted() = runBlocking {
        search("one")
        search("two")

        repository.deleteSearch("one")

        assertEquals(listOf("two"), suggestions(""))
    }

    @Test
    fun recentPlaysAreNewestFirst() = runBlocking {
        addSong("s1")
        addSong("s2")
        repository.recordPlay("s1", 10.seconds)
        clock.advance()
        repository.recordPlay("s2", 20.seconds)
        clock.advance()
        repository.recordPlay("s1", 30.seconds)

        val plays = repository.recentPlays(2).first()
        assertEquals(listOf("s1", "s2"), plays.map { it.songId })
        assertEquals(listOf(30.seconds, 20.seconds), plays.map { it.timeSpent })

        activeUser.id.value = "other"
        repository.clearPlays()
        activeUser.id.value = USER
        assertEquals(3, repository.recentPlays(10).first().size)

        repository.clearPlays()
        assertEquals(0, repository.recentPlays(10).first().size)
    }

    @Test
    fun updatingAPlayChangesOnlyItsTime() = runBlocking {
        addSong("s1")
        val first = repository.recordPlay("s1", 10.seconds)
        clock.advance()
        repository.recordPlay("s1", 20.seconds)

        repository.updatePlay(first, 45.seconds)

        val plays = repository.recentPlays(10).first()
        assertEquals(listOf(20.seconds, 45.seconds), plays.map { it.timeSpent })
        assertEquals(listOf("s1", "s1"), plays.map { it.songId })
    }

    private suspend fun librarySong(
        id: String,
        origin: Origin = Origin.LOCAL,
        likedAt: Instant? = null
    ) {
        addSong(id, origin)
        database.userSongDao().upsert(
            UserSongEntity(
                userId = USER,
                songId = id,
                likedAt = likedAt,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
    }

    /** A period from [from] up to [until], seconds of the test clock. */
    private fun period(from: Long, until: Long) = PlayPeriod(
        Instant.fromEpochMilliseconds(from * 1000),
        Instant.fromEpochMilliseconds(until * 1000),
    )

    /** Records a play of [songId] at second [at] of the test clock. */
    private suspend fun playAt(songId: String, at: Long, seconds: Int = 10) {
        clock.time = Instant.fromEpochMilliseconds(at * 1000)
        repository.recordPlay(songId, seconds.seconds)
    }

    private suspend fun played(
        periods: List<PlayPeriod>,
        origins: Set<Origin> = emptySet(),
        newestFirst: Boolean = true,
    ) = repository.plays(periods, origins, newestFirst).asList()
        .map { "${it.period}:${it.song.songId}" }

    @Test
    fun everyPlayIsARowInItsPeriod() = runBlocking {
        librarySong("s1")
        librarySong("s2")
        playAt("s1", 1)
        playAt("s1", 2)
        playAt("s2", 5)
        playAt("s1", 15)
        val periods = listOf(period(10, 20), period(0, 10))

        assertEquals(listOf("0:s1", "1:s2", "1:s1", "1:s1"), played(periods))
        assertEquals(listOf("1:s1", "1:s1", "1:s2", "0:s1"), played(periods, newestFirst = false))

        val plays = repository.plays(periods, emptySet(), true).asList()
        assertEquals(
            listOf(15_000L, 5_000, 2_000, 1_000),
            plays.map { it.playedAt.toEpochMilliseconds() },
        )
        assertEquals(
            plays.map { it.id },
            repository.listPlays(periods, emptySet(), true).map { it.id })
    }

    @Test
    fun playedSongsLeaveOutDislikedSongsAndSongsOutOfTheLibrary() = runBlocking {
        librarySong("liked", likedAt = clock.now())
        librarySong("disliked", likedAt = DISLIKED)
        addSong("gone")
        playAt("liked", 1)
        playAt("disliked", 2)
        playAt("gone", 3)

        assertEquals(listOf("0:liked"), played(listOf(period(0, 10))))
    }

    @Test
    fun playedSongsAreKeptToTheirPeriodsAndOrigins() = runBlocking {
        librarySong("local")
        librarySong("remote", Origin.YOUTUBE)
        playAt("local", 1)
        playAt("remote", 2)
        playAt("local", 20)

        assertEquals(listOf("0:remote", "0:local"), played(listOf(period(0, 10))))
        assertEquals(listOf("0:remote"), played(listOf(period(0, 30)), setOf(Origin.YOUTUBE)))
        assertEquals(listOf("0:local", "0:remote", "0:local"), played(listOf(period(0, 30))))
        assertEquals(emptyList<String>(), played(emptyList()))
    }

    @Test
    fun summariesCountSongsOnceAndEveryPlaysTime() = runBlocking {
        librarySong("s1")
        librarySong("s2")
        playAt("s1", 1, seconds = 30)
        playAt("s1", 2, seconds = 40)
        playAt("s2", 3, seconds = 50)
        playAt("s2", 15, seconds = 5)

        val summaries =
            repository.periodSummaries(listOf(period(10, 20), period(0, 10)), emptySet())
                .first()
                .sortedBy { it.period }
        assertEquals(listOf(0, 1), summaries.map { it.period })
        assertEquals(listOf(1, 3), summaries.map { it.plays })
        assertEquals(listOf(1, 2), summaries.map { it.songs })
        assertEquals(listOf(5.seconds, 120.seconds), summaries.map { it.timeSpent })
    }

    @Test
    fun deletingAPlayKeepsTheSongsOtherPlays() = runBlocking {
        librarySong("s1")
        playAt("s1", 1)
        playAt("s1", 2)
        val first = repository.recentPlays(10).first().last().id

        repository.deletePlay(first)

        assertEquals(
            listOf(2_000L),
            repository.recentPlays(10).first().map { it.createdAt.toEpochMilliseconds() })
    }

    @Test
    fun deletedPlaysComeBackAsTheyWere() = runBlocking {
        librarySong("s1")
        librarySong("s2")
        playAt("s1", 1)
        playAt("s2", 2)
        val before = repository.recentPlays(10).first()

        val one = repository.deletePlay(before.first().id)
        assertEquals(before.first(), one)
        assertNull(repository.deletePlay(before.first().id))
        repository.restorePlays(listOfNotNull(one))
        assertEquals(before, repository.recentPlays(10).first())

        val all = repository.clearPlays()
        assertEquals(0, repository.recentPlays(10).first().size)
        repository.restorePlays(all)
        assertEquals(before, repository.recentPlays(10).first())
    }

    @Test
    fun deletingWhatIsShownLeavesTheRestAlone() = runBlocking {
        librarySong("local")
        librarySong("remote", Origin.YOUTUBE)
        librarySong("disliked", likedAt = DISLIKED)
        playAt("local", 1)
        playAt("remote", 2)
        playAt("disliked", 3)
        playAt("remote", 20)

        repository.deletePlays(listOf(period(0, 10)), setOf(Origin.YOUTUBE))

        assertEquals(
            listOf("remote", "disliked", "local"),
            repository.recentPlays(10).first().map { it.songId },
        )

        activeUser.id.value = "other"
        repository.deletePlays(listOf(period(0, 30)), emptySet())
        activeUser.id.value = USER
        assertEquals(3, repository.recentPlays(10).first().size)
    }
}
