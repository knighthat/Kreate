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
import me.knighthat.kreate3.database.repository.AlbumRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalAlbum

/**
 * The album whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 */
class LocalAlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: AlbumRepository,
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
     * Whether the album can be shared: an album of the server when the user may make shares
     * there, or another album that has a link. `false` until that is known.
     */
    val canShare: StateFlow<Boolean> = album
        .map { it?.let { album -> album.origin to (album.url != null) } }
        .distinctUntilChanged()
        .map { found ->
            val (origin, hasLink) = found ?: return@map false
            if (origin == Origin.OPEN_SUBSONIC) openSubsonic.shareRole() != false else hasLink
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * A link to the album that anyone can open. For an album of the server it is asked of the
     * server at every tap, which gives a share it has already or makes one. Links aren't stored,
     * a share can expire or be deleted on the server.
     *
     * @return the link, or why there is none
     */
    suspend fun shareLink(): Result<String> = runCatching {
        val album = checkNotNull(album.value) { "The album isn't loaded" }
        if (album.origin != Origin.OPEN_SUBSONIC)
            return@runCatching checkNotNull(album.url) { "This album has no link" }

        checkNotNull(openSubsonic.shareLink(Shareable.Album(album.id), album.name)) {
            "The server is turned off"
        }
    }
}
