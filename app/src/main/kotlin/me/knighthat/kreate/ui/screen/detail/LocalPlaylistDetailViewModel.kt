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

package me.knighthat.kreate.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate.database.repository.PlaylistRepository
import me.knighthat.kreate.database.repository.UserSongRepository
import me.knighthat.kreate.ui.component.toMediaItems
import me.knighthat.kreate.ui.navigation.LocalPlaylist

/**
 * The playlist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 */
class LocalPlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: PlaylistRepository,
    songRepository: UserSongRepository,
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle[LocalPlaylist.ID]) {
        "LocalPlaylist.ID is missing from the saved state"
    }

    val playlist = repository.find(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs = songRepository.ofPlaylist(playlistId)
        .toMediaItems()
        .cachedIn(viewModelScope)
}
