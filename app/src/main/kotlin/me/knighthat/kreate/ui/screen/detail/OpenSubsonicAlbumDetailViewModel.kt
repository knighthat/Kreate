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
import me.knighthat.kreate.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate.ui.navigation.LocalAlbum

/**
 * Brings the album whose id is in the saved state, and its songs, from the server.
 */
class OpenSubsonicAlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val library: OpenSubsonicLibrary,
) : SyncDetailViewModel() {

    val albumId: String = checkNotNull(savedStateHandle[LocalAlbum.ID]) {
        "LocalAlbum.ID is missing from the saved state"
    }

    init {
        sync()
    }

    override suspend fun fetchAndStore() = library.syncAlbum(albumId)
}
