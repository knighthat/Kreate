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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalPlaylist

/**
 * The playlist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 */
class LocalPlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: PlaylistRepository,
    songRepository: UserSongRepository,
    private val openSubsonic: OpenSubsonicProvider,
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle[LocalPlaylist.ID]) {
        "LocalPlaylist.ID is missing from the saved state"
    }

    val playlist = repository.find(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs = songRepository.ofPlaylist(playlistId)
        .toMediaItems()
        .cachedIn(viewModelScope)

    /**
     * Whether the playlist can be shared: it is a playlist of the server and the user may make
     * shares there. `false` until that is known.
     */
    val canShare: StateFlow<Boolean> = playlist
        .map { it?.origin == Origin.OPEN_SUBSONIC && it.remoteId != null }
        .distinctUntilChanged()
        .map { fromServer -> fromServer && openSubsonic.shareRole() != false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * A link to the playlist that anyone can open, asked of the server at every tap. It gives a
     * share it has already or makes one. Links aren't stored, a share can expire or be deleted on
     * the server.
     *
     * @return the link, or why there is none
     */
    suspend fun shareLink(): Result<String> = runCatching {
        val playlist = checkNotNull(playlist.value) { "The playlist isn't loaded" }
        val remoteId = checkNotNull(playlist.remoteId) { "This playlist isn't on the server" }

        checkNotNull(openSubsonic.shareLink(Shareable.Playlist(remoteId), playlist.name)) {
            "The server is turned off"
        }
    }
}
