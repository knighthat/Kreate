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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch
import me.knighthat.kreate3.auto.AutoLibrary
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.livePlaylists
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * The playlists of the active user, then their playlists on the external sources. Pages are kept while the screen lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomePlaylistsViewModel internal constructor(
    private val playlistRepository: PlaylistRepository,
    private val library: AutoLibrary,
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

    private val _monthlyOnly = MutableStateFlow(false)

    /**
     * Whether the monthly playlists can be picked alone: they are the app's own, only while the
     * user has some and the origin filter shows them.
     */
    val offersMonthly: StateFlow<Boolean> =
        combine(playlistRepository.origins(), filter.selected) { stored, selected ->
            Origin.LOCAL in stored && (selected.isEmpty() || Origin.LOCAL in selected)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether the tab shows only the monthly playlists, off at first. It lives with the view model,
     * as long as the tab is in the back stack; it is kept while [offersMonthly] is off, and applies
     * again once it is on.
     */
    val monthlyOnly: StateFlow<Boolean> = _monthlyOnly.asStateFlow()

    // The filter as it applies
    private val monthly = combine(_monthlyOnly, offersMonthly) { only, offered -> only && offered }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val playlists = combine(filter.selected, monthly, ::Pair)
        .flatMapLatest { (origins, only) ->
            playlistRepository.all(origins = origins, monthlyOnly = only)
        }
        .map { page -> page.map { it.toMediaItem() } }
        .cachedIn(viewModelScope)

    /** The playlists of the server and of YouTube, fetched live and never stored. */
    val live: StateFlow<List<MediaItem>> =
        // None of them is monthly
        combine(filter.live, monthly) { list, only -> if (only) emptyList() else list }
            .stateIn(viewModelScope, SharingStarted.Eagerly, filter.live.value)

    /**
     * Fetches the playlists of the server and of YouTube again, call it when the tab shows. Each
     * adds nothing while it isn't set up.
     */
    fun refresh() = remote.refresh()

    /** Turns [monthlyOnly] around. */
    fun toggleMonthlyOnly() = _monthlyOnly.update { !it }

    /** Makes an empty playlist called [name] in the app. */
    fun create(name: String) {
        viewModelScope.launch {
            playlistRepository.save(UserPlaylist(origin = Origin.LOCAL, name = name.trim()))
        }
    }

    /** One playlist of the list at random, `null` when the list is empty. */
    suspend fun random(): MediaItem? {
        val origins = filter.selected.value
        val only = monthly.value
        return pickRandom(playlistRepository.count(origins, only), live.value) {
            playlistRepository.random(origins, only)?.toMediaItem()
        }
    }

    /**
     * The songs of one playlist of the list at random, as tapping it plays them; empty when the
     * list is, or the playlist's source can't give them.
     */
    suspend fun randomSongs(): List<MediaItem> {
        val playlist = random() ?: return emptyList()
        return try {
            library.songsOf(Tab.PLAYLISTS, playlist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get the songs of ${playlist.mediaId}", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "HomePlaylists"
    }
}
