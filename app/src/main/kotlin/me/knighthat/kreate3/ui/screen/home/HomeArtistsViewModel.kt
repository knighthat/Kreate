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

package me.knighthat.kreate3.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.ui.component.toMediaItem

/**
 * The artists in the active user's library. Pages are kept while the screen lives.
 */
class HomeArtistsViewModel(
    artistRepository: UserArtistRepository,
    private val library: OpenSubsonicLibrary,
    private val youtubeLibrary: YouTubeLibrary,
) : ViewModel() {

    val artists = artistRepository.all()
        .map { page -> page.map { it.toMediaItem() } }
        .cachedIn(viewModelScope)

    /**
     * Brings the artists of the server and of YouTube into the library, call it when the tab
     * shows. Each does nothing while it isn't set up.
     */
    fun refresh() {
        library.refresh(LibrarySection.ARTISTS)
        youtubeLibrary.refresh(LibrarySection.ARTISTS)
    }
}
