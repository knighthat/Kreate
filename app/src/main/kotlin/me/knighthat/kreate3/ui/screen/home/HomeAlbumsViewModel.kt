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
import me.knighthat.kreate3.auto.AutoLibrary
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.liveAlbums
import me.knighthat.kreate3.ui.component.isBookmarked
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * The albums in the active user's library, then the ones saved on the external sources. Pages are kept while the screen lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAlbumsViewModel internal constructor(
    private val albumRepository: UserAlbumRepository,
    private val library: AutoLibrary,
    openSubsonic: OpenSubsonicProvider,
    youTube: YouTubeProvider,
) : ViewModel() {

    private val remote = LiveItems(viewModelScope) { liveAlbums(openSubsonic, youTube) }

    val filter = OriginFilter(
        viewModelScope,
        Preferences.HOME_ALBUMS_ORIGINS,
        stored = albumRepository.origins(),
        liveItems = remote.items,
    )

    private val _bookmarkedOnly = MutableStateFlow(false)

    /**
     * Whether the tab shows only the albums the user bookmarked, off at first. It lives with the
     * view model, as long as the tab is in the back stack.
     */
    val bookmarkedOnly: StateFlow<Boolean> = _bookmarkedOnly.asStateFlow()

    val albums = combine(filter.selected, _bookmarkedOnly, ::Pair)
        .flatMapLatest { (origins, only) ->
            albumRepository.all(origins = origins, bookmarkedOnly = only)
        }
        .map { page -> page.map { it.toMediaItem() } }
        .cachedIn(viewModelScope)

    /** The albums of the server and of YouTube, fetched live and never stored. */
    val live: StateFlow<List<MediaItem>> =
        combine(filter.live, _bookmarkedOnly) { list, only ->
            if (only) list.filter { it.isBookmarked } else list
        }.stateIn(viewModelScope, SharingStarted.Eagerly, filter.live.value)

    /**
     * Fetches the albums of the server and of YouTube again, call it when the tab shows. Each
     * adds nothing while it isn't set up.
     */
    fun refresh() = remote.refresh()

    /** Turns [bookmarkedOnly] around. */
    fun toggleBookmarkedOnly() = _bookmarkedOnly.update { !it }

    /** One album of the list at random, `null` when the list is empty. */
    suspend fun random(): MediaItem? {
        val origins = filter.selected.value
        val only = _bookmarkedOnly.value
        return pickRandom(albumRepository.count(origins, only), live.value) {
            albumRepository.random(origins, only)?.toMediaItem()
        }
    }

    /**
     * The songs of one album of the list at random, as tapping it plays them; empty when the list
     * is, or the album's source can't give them.
     */
    suspend fun randomSongs(): List<MediaItem> {
        val album = random() ?: return emptyList()
        return try {
            library.songsOf(Tab.ALBUMS, album)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get the songs of ${album.mediaId}", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "HomeAlbums"
    }
}
