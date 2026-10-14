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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.repository.HistoryRepository
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * Suggestions for the [text] being typed: the library items that match it and the earlier
 * searches that start with it. The [origin], chosen in the config, says where to look.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    songRepository: UserSongRepository,
    albumRepository: UserAlbumRepository,
    artistRepository: UserArtistRepository,
    playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository,
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

    /** Where the search looks. Only [Origin.LOCAL], the library on this device, finds anything yet. */
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

    /** Earlier searches that start with the [text], most recent first. */
    val recentSearches: StateFlow<List<SearchHistory>> = _text
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { historyRepository.searchesStartingWith(it, RECENT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                // No online catalog is connected yet, so only local searches find anything
                if (query.isEmpty() || origin != Origin.LOCAL) flowOf(PagingData.empty())
                else search(query)
            }
            .cachedIn(viewModelScope)

    private companion object {
        const val RECENT_LIMIT = 10
        const val QUERY_DELAY_MS = 250L
    }
}
