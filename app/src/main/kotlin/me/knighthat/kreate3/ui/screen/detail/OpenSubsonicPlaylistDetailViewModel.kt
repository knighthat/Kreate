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
import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.ui.navigation.LocalPlaylist

/**
 * Brings the songs of the playlist whose id is in the saved state from the server. The id is the
 * playlist's in the database, the server's is read from it.
 */
class OpenSubsonicPlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val library: OpenSubsonicLibrary,
) : SyncDetailViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle[LocalPlaylist.ID]) {
        "LocalPlaylist.ID is missing from the saved state"
    }

    init {
        sync()
    }

    override suspend fun fetchAndStore(): Boolean {
        val playlist =
            checkNotNull(repository.find(playlistId).first()) { "This playlist no longer exists" }
        val remoteId = checkNotNull(playlist.remoteId) { "This playlist isn't on the server" }
        return library.syncPlaylist(playlistId, remoteId)
    }
}
