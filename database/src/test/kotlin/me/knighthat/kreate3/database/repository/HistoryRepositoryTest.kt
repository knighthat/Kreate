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
import me.knighthat.kreate3.database.paging.SearchSort
import me.knighthat.kreate3.database.paging.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

internal class HistoryRepositoryTest : DatabaseTest() {

    private lateinit var repository: HistoryRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        addUser()
        addUser("other")
        repository = HistoryRepositoryImpl(
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
}
