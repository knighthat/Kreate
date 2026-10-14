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

package me.knighthat.kreate.ui.screen.search

import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.PlaybackHistory
import me.knighthat.kreate.database.model.SearchHistory
import me.knighthat.kreate.database.paging.SearchSort
import me.knighthat.kreate.database.paging.SortOrder
import me.knighthat.kreate.database.repository.HistoryRepository
import me.knighthat.kreate.ui.navigation.Tab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Collections
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private class History : HistoryRepository {
        val searches = MutableStateFlow(listOf<String>())
        val prefixes: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun searches(order: SortOrder<SearchSort>): Flow<PagingData<SearchHistory>> =
            flowOf(PagingData.empty())

        override fun searchesStartingWith(prefix: String, limit: Int): Flow<List<SearchHistory>> {
            prefixes += prefix
            return searches.map { all ->
                all.filter { it.startsWith(prefix) }.take(limit).map(::SearchHistory)
            }
        }

        override suspend fun recordSearch(query: String) {
            searches.value = listOf(query) + (searches.value - query)
        }

        override suspend fun deleteSearch(query: String) = Unit
        override suspend fun clearSearches() = Unit
        override fun recentPlays(limit: Int): Flow<List<PlaybackHistory>> = flowOf(emptyList())
        override suspend fun recordPlay(songId: String, timeSpent: Duration) = Unit
        override suspend fun clearPlays() = Unit
    }

    /** Searches asked of the catalog, as `<repository> <query>`. */
    private val searched: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** A repository whose lists are all empty, it only remembers what was searched. */
    private inline fun <reified T> catalog(): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "search" -> {
                    searched += "${T::class.simpleName} ${args[0]}"
                    flowOf(PagingData.empty<Any>())
                }

                "toString" -> T::class.simpleName
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                else -> throw UnsupportedOperationException(method.name)
            }
        } as T

    private val history = History()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = SearchViewModel(catalog(), catalog(), catalog(), catalog(), history)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theQueryWaitsForTypingToPause() = runTest {
        viewModel.setText("que")
        advanceTimeBy(100)
        viewModel.setText("  queen ")
        advanceTimeBy(200)
        runCurrent()
        assertEquals("", viewModel.query.value)

        advanceTimeBy(100)
        runCurrent()
        assertEquals("  queen ", viewModel.text.value)
        assertEquals("queen", viewModel.query.value)
    }

    @Test
    fun everyCatalogIsSearchedForTheQuery() = runTest {
        listOf(viewModel.songs, viewModel.albums, viewModel.artists, viewModel.playlists).forEach {
            backgroundScope.launch { it.collect {} }
        }

        viewModel.setText("abba")
        advanceUntilIdle()

        assertEquals(
            setOf(
                "UserSongRepository abba",
                "UserAlbumRepository abba",
                "UserArtistRepository abba",
                "PlaylistRepository abba",
            ),
            searched.toSet()
        )
    }

    @Test
    fun nothingIsSearchedWithoutQueryOrOutsideTheDevice() = runTest {
        backgroundScope.launch { viewModel.songs.collect {} }

        viewModel.setText("   ")
        advanceUntilIdle()
        viewModel.setOrigin(Origin.OPEN_SUBSONIC)
        viewModel.setText("abba")
        advanceUntilIdle()

        assertTrue(searched.isEmpty())

        viewModel.setOrigin(Origin.LOCAL)
        advanceUntilIdle()
        assertEquals(listOf("UserSongRepository abba"), searched.toList())
    }

    @Test
    fun aSubmittedSearchIsRememberedTrimmed() = runTest {
        viewModel.setText(" queen ")
        viewModel.submit()
        viewModel.setText("   ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf("queen"), history.searches.value)
    }

    @Test
    fun recentSearchesFollowWhatIsTypedRightAway() = runTest {
        history.searches.value = listOf("queen", "quiet riot", "abba")
        backgroundScope.launch { viewModel.recentSearches.collect {} }

        viewModel.setText(" qu")
        runCurrent()

        assertEquals(listOf("queen", "quiet riot"), viewModel.recentSearches.value.map { it.query })
        assertEquals("qu", history.prefixes.last())
    }

    @Test
    fun categoriesAreTurnedOnAndOff() {
        assertEquals(Tab.entries.toSet(), viewModel.categories.value)

        viewModel.setCategory(Tab.ALBUMS, false)
        viewModel.setCategory(Tab.ARTISTS, false)
        viewModel.setCategory(Tab.ALBUMS, true)

        assertEquals(setOf(Tab.SONGS, Tab.ALBUMS, Tab.PLAYLISTS), viewModel.categories.value)
    }
}
