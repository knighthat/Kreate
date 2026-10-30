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

package me.knighthat.kreate3.ui.screen.search

import androidx.paging.PagingData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.database.model.PeriodPlay
import me.knighthat.kreate3.database.model.PeriodSummary
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.paging.SearchSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.repository.HistoryRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.ui.navigation.Tab
import okhttp3.OkHttpClient
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
        override fun plays(periods: List<PlayPeriod>, origins: Set<Origin>, newestFirst: Boolean) =
            flowOf(PagingData.empty<PeriodPlay>())

        override suspend fun listPlays(
            periods: List<PlayPeriod>,
            origins: Set<Origin>,
            newestFirst: Boolean,
        ) = emptyList<PeriodPlay>()

        override fun periodSummaries(periods: List<PlayPeriod>, origins: Set<Origin>) =
            flowOf(emptyList<PeriodSummary>())

        override suspend fun recordPlay(songId: String, timeSpent: Duration) = 0L
        override suspend fun updatePlay(id: Long, timeSpent: Duration) = Unit
        override suspend fun deletePlay(id: Long): PlaybackHistory? = null
        override suspend fun deletePlays(periods: List<PlayPeriod>, origins: Set<Origin>) =
            emptyList<PlaybackHistory>()

        override suspend fun clearPlays() = emptyList<PlaybackHistory>()
        override suspend fun restorePlays(plays: List<PlaybackHistory>) = Unit
    }

    /** Searches asked of the catalog, as `<repository> <query>`. */
    private val searched: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /**
     * A repository whose lists are all empty, it only remembers what was searched. No song is
     * disliked.
     */
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

                "dislikedIds" -> flowOf(emptySet<String>())
                "toString" -> T::class.simpleName
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                else -> throw UnsupportedOperationException(method.name)
            }
        } as T

    private val history = History()

    /** Bodies of the requests sent to YouTube. */
    private val youtubeRequests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // Suggests searches only, items would need Android to be shown
    private val youtube = YouTubeProvider(
        HttpClient(MockEngine { request ->
            youtubeRequests += request.body.toByteArray().decodeToString()
            respond(
                content = SUGGESTIONS,
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
        }),
        Json,
        // Nobody is signed in
        cookies = { "" },
        pageId = { "" },
        // The setting needs Koin, which tests don't start
        region = { "US" },
    )

    // Never asked here: it reads the profile's settings, which tests don't have
    private val openSubsonic = OpenSubsonicProvider(OkHttpClient(), Json)

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = SearchViewModel(
            catalog(), catalog(), catalog(), catalog(), history, youtube, openSubsonic
        )
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
        viewModel.setOrigin(Origin.YOUTUBE)
        viewModel.setText("abba")
        advanceUntilIdle()

        assertTrue(searched.isEmpty())

        viewModel.setOrigin(Origin.LOCAL)
        advanceUntilIdle()
        assertEquals(listOf("UserSongRepository abba"), searched.toList())
    }

    @Test
    fun youTubeSuggestsOnlyWhileItIsWhereTheSearchLooks() = runTest {
        backgroundScope.launch { viewModel.liveSuggestions.collect {} }

        viewModel.setText("abba")
        advanceUntilIdle()
        assertTrue(youtubeRequests.isEmpty())
        assertEquals(LiveSuggestions.EMPTY, viewModel.liveSuggestions.value)

        viewModel.setOrigin(Origin.YOUTUBE)
        // The answer comes on the client's own thread
        val found = viewModel.liveSuggestions.first { it != LiveSuggestions.EMPTY }
        assertEquals(listOf("abba gold", "abba waterloo"), found.queries)
        assertTrue(youtubeRequests.single().contains("\"input\":\"abba\""))
        // The library isn't searched meanwhile
        assertTrue(searched.isEmpty())
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

    private companion object {
        const val SUGGESTIONS = """{"contents":[{"searchSuggestionsSectionRenderer":{"contents":[
            {"searchSuggestionRenderer":{"navigationEndpoint":{"searchEndpoint":{"query":"abba gold"}}}},
            {"searchSuggestionRenderer":{"navigationEndpoint":{"searchEndpoint":{"query":"abba waterloo"}}}}
        ]}}]}"""
    }
}
