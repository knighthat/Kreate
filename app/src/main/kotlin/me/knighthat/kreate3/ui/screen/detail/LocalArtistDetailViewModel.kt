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

package me.knighthat.kreate3.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalArtist

/**
 * The artist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 */
class LocalArtistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: ArtistRepository,
    songRepository: UserSongRepository,
    albumRepository: UserAlbumRepository,
) : ViewModel() {

    val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    val artist = repository.find(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums = albumRepository.ofArtist(artistId, ALBUM_LIMIT)
        .map { albums -> albums.map { it.toMediaItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs = songRepository.ofArtist(artistId)
        .toMediaItems()
        .cachedIn(viewModelScope)

    private companion object {
        /** Albums shown on the page, the artist may have more. */
        const val ALBUM_LIMIT = 10
    }
}
