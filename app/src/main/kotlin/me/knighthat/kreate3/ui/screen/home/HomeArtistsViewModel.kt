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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.liveArtists
import me.knighthat.kreate3.ui.component.isFollowed
import me.knighthat.kreate3.ui.component.toMediaItem

/**
 * The artists in the active user's library, then the ones followed on the external sources. Pages are kept while the screen lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeArtistsViewModel(
    private val artistRepository: UserArtistRepository,
    openSubsonic: OpenSubsonicProvider,
    youTube: YouTubeProvider,
) : ViewModel() {

    private val remote = LiveItems(viewModelScope) { liveArtists(openSubsonic, youTube) }

    val filter = OriginFilter(
        viewModelScope,
        Preferences.HOME_ARTISTS_ORIGINS,
        stored = artistRepository.origins(),
        liveItems = remote.items,
    )

    private val _followedOnly = MutableStateFlow(false)

    /**
     * Whether the tab shows only the artists the user follows, off at first. It lives with the view
     * model, as long as the tab is in the back stack.
     */
    val followedOnly: StateFlow<Boolean> = _followedOnly.asStateFlow()

    val artists = combine(filter.selected, _followedOnly, ::Pair)
        .flatMapLatest { (origins, only) ->
            artistRepository.all(origins = origins, followedOnly = only)
        }
        .map { page -> page.map { it.toMediaItem() } }
        .cachedIn(viewModelScope)

    /** The artists of the server and of YouTube, fetched live and never stored. */
    val live: StateFlow<List<MediaItem>> =
        combine(filter.live, _followedOnly) { list, only ->
            if (only) list.filter { it.isFollowed } else list
        }.stateIn(viewModelScope, SharingStarted.Eagerly, filter.live.value)

    /**
     * Fetches the artists of the server and of YouTube again, call it when the tab shows. Each
     * adds nothing while it isn't set up.
     */
    fun refresh() = remote.refresh()

    /** Turns [followedOnly] around. */
    fun toggleFollowedOnly() = _followedOnly.update { !it }

    /** One artist of the list at random, `null` when the list is empty. */
    suspend fun random(): MediaItem? {
        val origins = filter.selected.value
        val only = _followedOnly.value
        return pickRandom(artistRepository.count(origins, only), live.value) {
            artistRepository.random(origins, only)?.toMediaItem()
        }
    }
}
