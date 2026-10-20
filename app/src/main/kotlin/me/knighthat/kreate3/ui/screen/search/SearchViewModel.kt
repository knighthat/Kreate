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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.repository.HistoryRepository
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.opensubsonic.suggestions
import me.knighthat.kreate3.source.youtube.tab
import me.knighthat.kreate3.source.youtube.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * Suggestions from an online catalog: whole searches, and items that match already, each with the
 * kind it is.
 */
data class LiveSuggestions(val queries: List<String>, val items: List<Pair<Tab, MediaItem>>) {

    companion object {
        val EMPTY = LiveSuggestions(emptyList(), emptyList())
    }
}

/**
 * Suggestions for the [text] being typed: the library items that match it and the earlier
 * searches that start with it, or what YouTube or the server suggests. The [origin], chosen in the
 * config, says where to look.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    songRepository: UserSongRepository,
    albumRepository: UserAlbumRepository,
    artistRepository: UserArtistRepository,
    playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository,
    private val youtube: YouTubeProvider,
    private val openSubsonic: OpenSubsonicProvider,
) : ViewModel() {

    private val _text = MutableStateFlow("")
    private val _categories = MutableStateFlow(Tab.entries.toSet())
    private val _origin = MutableStateFlow(Origin.LOCAL)

    /** What the user typed so far. */
    val text: StateFlow<String> = _text.asStateFlow()

    /** [text] without the spaces around it, once the user paused typing. */
    val query: StateFlow<String> = _text
        .debounce(QUERY_DELAY_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Where the search looks. */
    val origin: StateFlow<Origin> = _origin.asStateFlow()

    /** What the results show, each [Tab] stands for the kind of item it lists. */
    val categories: StateFlow<Set<Tab>> = _categories.asStateFlow()

    val songs = results { songRepository.search(it).toMediaItems() }

    val albums = results { query ->
        albumRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    val artists = results { query ->
        artistRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    val playlists = results { query ->
        playlistRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    /**
     * What YouTube or the server suggests for the [query] while [origin] is one of them, asked
     * live and never stored. The server suggests items only. A failed request suggests nothing.
     */
    val liveSuggestions: StateFlow<LiveSuggestions> = combine(query, _origin, ::Pair)
        .mapLatest { (query, origin) ->
            if (query.isEmpty()) return@mapLatest LiveSuggestions.EMPTY
            when (origin) {
                Origin.LOCAL -> LiveSuggestions.EMPTY
                Origin.YOUTUBE -> youTubeSuggestions(query)
                Origin.OPEN_SUBSONIC -> serverSuggestions(query)
            }
        }
        // Turned into items off the main thread, while the user types
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveSuggestions.EMPTY)

    /** Earlier searches that start with the [text], most recent first. */
    val recentSearches: StateFlow<List<SearchHistory>> = _text
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { historyRepository.searchesStartingWith(it, RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun youTubeSuggestions(query: String): LiveSuggestions =
        try {
            val found = youtube.suggestions(query)
            LiveSuggestions(
                queries = found.queries.distinct(),
                items = found.items.map { it.tab to it.toMediaItem(youtube) }
                    .distinctBy { (tab, item) -> tab to item.mediaId },
            )
        } catch (e: YouTubeException) {
            Log.w(TAG, "Couldn't get suggestions from YouTube", e)
            LiveSuggestions.EMPTY
        }

    private suspend fun serverSuggestions(query: String): LiveSuggestions =
        try {
            LiveSuggestions(queries = emptyList(), items = openSubsonic.suggestions(query))
        } catch (e: ServerException) {
            Log.w(TAG, "Couldn't get suggestions from the server", e)
            LiveSuggestions.EMPTY
        }

    fun setText(text: String) {
        _text.value = text
    }

    /** Remembers the [text] as a search. */
    fun submit() {
        val search = _text.value.trim()
        if (search.isEmpty()) return
        viewModelScope.launch { historyRepository.recordSearch(search) }
    }

    fun setOrigin(origin: Origin) {
        _origin.value = origin
    }

    fun setCategory(category: Tab, enabled: Boolean) =
        _categories.update { if (enabled) it + category else it - category }

    private fun <T : Any> results(search: (String) -> Flow<PagingData<T>>): Flow<PagingData<T>> =
        combine(query, _origin, ::Pair)
            .flatMapLatest { (query, origin) ->
                // Online catalogs suggest through liveSuggestions
                if (query.isEmpty() || origin != Origin.LOCAL) flowOf(PagingData.empty())
                else search(query)
            }
            .cachedIn(viewModelScope)

    private companion object {
        const val TAG = "SearchViewModel"
        const val RECENT_LIMIT = 10
        const val QUERY_DELAY_MS = 250L
    }
}
