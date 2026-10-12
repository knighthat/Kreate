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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.repository.PlaylistRepository
import me.knighthat.kreate.database.repository.UserAlbumRepository
import me.knighthat.kreate.database.repository.UserArtistRepository
import me.knighthat.kreate.database.repository.UserSongRepository
import me.knighthat.kreate.ui.component.toMediaItem
import me.knighthat.kreate.ui.component.toMediaItems
import me.knighthat.kreate.ui.navigation.SearchResults

/**
 * What the search in the saved state found, by kind. Pages are kept while the screen lives.
 */
class SearchResultsViewModel(
    savedStateHandle: SavedStateHandle,
    songRepository: UserSongRepository,
    albumRepository: UserAlbumRepository,
    artistRepository: UserArtistRepository,
    playlistRepository: PlaylistRepository,
) : ViewModel() {

    val query: String = checkNotNull(savedStateHandle[SearchResults.QUERY]) {
        "SearchResults.QUERY is missing from the saved state"
    }

    val origin: Origin = Origin.valueOf(
        checkNotNull(savedStateHandle[SearchResults.ORIGIN]) {
            "SearchResults.ORIGIN is missing from the saved state"
        }
    )

    val songs = results { songRepository.search(query).toMediaItems() }

    val albums = results {
        albumRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    val artists = results {
        artistRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    val playlists = results {
        playlistRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    private fun <T : Any> results(search: () -> Flow<PagingData<T>>): Flow<PagingData<T>> =
        // No online catalog is connected yet, so only local searches find anything
        (if (origin == Origin.LOCAL) search() else flowOf(PagingData.empty()))
            .cachedIn(viewModelScope)
}
