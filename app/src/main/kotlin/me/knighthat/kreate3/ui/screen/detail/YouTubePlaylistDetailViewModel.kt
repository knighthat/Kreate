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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.YouTubePlaylist

/**
 * A playlist of YouTube as its screen shows it, its songs are [YouTubePlaylistDetailViewModel.songs].
 */
data class YouTubePlaylistDetail(
    val name: String,
    val thumbnail: Uri?,
    val subtitle: String?,
)

/**
 * Fetches the playlist whose id is in the saved state from YouTube, and its songs part after part
 * as they are scrolled to. Nothing is stored.
 */
class YouTubePlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: YouTubeProvider,
) : LiveDetailViewModel<YouTubePlaylistDetail>() {

    val playlistId: String = checkNotNull(savedStateHandle[YouTubePlaylist.ID]) {
        "YouTubePlaylist.ID is missing from the saved state"
    }

    override val notFound = "YouTube doesn't have this playlist"

    private val _songs = MutableStateFlow(emptyList<MediaItem>())
    private val _hasMore = MutableStateFlow(false)
    private var continuation: String? = null
    private var more: Job? = null

    /** The songs fetched so far, in the playlist's order, each once. */
    val songs: StateFlow<List<MediaItem>> = _songs.asStateFlow()

    /** Whether YouTube has songs after [songs], see [loadMore]. */
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    init {
        load()
    }

    override suspend fun fetch(): YouTubePlaylistDetail? {
        more?.cancel()
        val page = provider.playlistPage(playlistId) ?: return null
        _songs.value = emptyList()
        add(page.songs, page.continuation)
        return YouTubePlaylistDetail(
            name = page.playlist.name,
            thumbnail = provider.artwork(page.playlist.thumbnail, HEADER_ARTWORK),
            subtitle = page.author,
        )
    }

    /**
     * Fetches the next songs unless they are on their way or there are none. A failure leaves
     * the list as it is, the next call tries again.
     */
    fun loadMore() {
        val token = continuation ?: return
        if (more?.isActive == true) return
        more = viewModelScope.launch {
            try {
                val page = provider.playlistSongs(token)
                add(page.items, page.continuation)
            } catch (_: YouTubeException) {
                // Scrolling to the end again retries
            }
        }
    }

    private fun add(songs: List<RemoteSong>, next: String?) {
        val items = songs.map {
            it.toLiveMediaItem(
                provider.artwork(
                    it.song.thumbnail ?: it.album?.thumbnail,
                    SONG_ARTWORK
                )
            )
        }
        _songs.update { shown ->
            val ids = shown.mapTo(HashSet()) { it.mediaId }
            shown + items.filter { ids.add(it.mediaId) }
        }
        continuation = next
        _hasMore.value = next != null
    }

    /** The playlist's public link, it needs no request. */
    fun shareLink(): Result<String> = Result.success(YouTubeLinks.playlist(playlistId))
}
