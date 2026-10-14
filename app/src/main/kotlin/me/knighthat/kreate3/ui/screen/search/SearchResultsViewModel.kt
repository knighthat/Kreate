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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.searchYouTube
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.SearchResults
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * What the search in the saved state found, by kind. Pages are kept while the screen lives.
 * YouTube's results are fetched live and never stored.
 */
class SearchResultsViewModel(
    savedStateHandle: SavedStateHandle,
    songRepository: UserSongRepository,
    albumRepository: UserAlbumRepository,
    artistRepository: UserArtistRepository,
    playlistRepository: PlaylistRepository,
    private val youtube: YouTubeProvider,
) : ViewModel() {

    val query: String = checkNotNull(savedStateHandle[SearchResults.QUERY]) {
        "SearchResults.QUERY is missing from the saved state"
    }

    val origin: Origin = Origin.valueOf(
        checkNotNull(savedStateHandle[SearchResults.ORIGIN]) {
            "SearchResults.ORIGIN is missing from the saved state"
        }
    )

    val songs = results(Tab.SONGS) { songRepository.search(query).toMediaItems() }

    val albums = results(Tab.ALBUMS) {
        albumRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    val artists = results(Tab.ARTISTS) {
        artistRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    val playlists = results(Tab.PLAYLISTS) {
        playlistRepository.search(query).map { page -> page.map { it.toMediaItem() } }
    }

    /** @param local searches the library on this device */
    private fun results(
        tab: Tab,
        local: () -> Flow<PagingData<MediaItem>>
    ): Flow<PagingData<MediaItem>> =
        when (origin) {
            Origin.LOCAL -> local()
            Origin.YOUTUBE -> searchYouTube(youtube, query, tab)
            // The server isn't searched yet
            Origin.OPEN_SUBSONIC -> flowOf(PagingData.empty())
        }.cachedIn(viewModelScope)
}
