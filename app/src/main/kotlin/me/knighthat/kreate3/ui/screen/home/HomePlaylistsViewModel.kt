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
import androidx.media3.common.MediaItem
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.livePlaylists
import me.knighthat.kreate3.ui.component.toMediaItem

/**
 * The playlists of the active user, then their playlists on the external sources. Pages are kept while the screen lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomePlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    openSubsonic: OpenSubsonicProvider,
    youTube: YouTubeProvider,
) : ViewModel() {

    private val remote = LiveItems(viewModelScope) { livePlaylists(openSubsonic, youTube) }

    val filter = OriginFilter(
        viewModelScope,
        Preferences.HOME_PLAYLISTS_ORIGINS,
        stored = playlistRepository.origins(),
        liveItems = remote.items,
    )

    val playlists = filter.selected
        .flatMapLatest { playlistRepository.all(origins = it) }
        .map { page -> page.map { it.toMediaItem() } }
        .cachedIn(viewModelScope)

    /** The playlists of the server and of YouTube, fetched live and never stored. */
    val live = filter.live

    /**
     * Fetches the playlists of the server and of YouTube again, call it when the tab shows. Each
     * adds nothing while it isn't set up.
     */
    fun refresh() = remote.refresh()

    /** One playlist of the list at random, `null` when the list is empty. */
    suspend fun random(): MediaItem? {
        val origins = filter.selected.value
        return pickRandom(playlistRepository.count(origins), live.value) {
            playlistRepository.random(origins)?.toMediaItem()
        }
    }
}
