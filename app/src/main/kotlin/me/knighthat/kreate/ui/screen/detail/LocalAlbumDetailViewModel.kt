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
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.repository.AlbumRepository
import me.knighthat.kreate.database.repository.UserSongRepository
import me.knighthat.kreate.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate.ui.component.toMediaItems
import me.knighthat.kreate.ui.navigation.LocalAlbum

/**
 * The album whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 */
class LocalAlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    songRepository: UserSongRepository,
    private val openSubsonic: OpenSubsonicProvider,
) : ViewModel() {

    val albumId: String = checkNotNull(savedStateHandle[LocalAlbum.ID]) {
        "LocalAlbum.ID is missing from the saved state"
    }

    val album = repository.find(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs = songRepository.ofAlbum(albumId)
        .toMediaItems()
        .cachedIn(viewModelScope)

    /**
     * Whether the album can be shared: it has a link, or its server can make one.
     */
    fun canShare(album: Album): Boolean =
        album.url != null || album.origin == Origin.OPEN_SUBSONIC

    /**
     * A link to the album that anyone can open. An OpenSubsonic album gets a share made on the
     * server the first time, which is kept and used again while the server still has it.
     *
     * @return the link, or why there is none
     */
    suspend fun shareLink(): Result<String> = runCatching {
        val album = checkNotNull(album.value) { "The album isn't loaded" }
        if (album.origin != Origin.OPEN_SUBSONIC)
            return@runCatching checkNotNull(album.url) { "This album has no link" }

        // A share deleted or expired on the server is made again
        album.url?.takeIf { openSubsonic.shareUrls()?.contains(it) == true }
            ?.let { return@runCatching it }

        val link = checkNotNull(openSubsonic.createShare(album.id, album.name)) {
            "The server is turned off"
        }
        repository.update(album.id) { it.copy(url = link) }
        link
    }
}
