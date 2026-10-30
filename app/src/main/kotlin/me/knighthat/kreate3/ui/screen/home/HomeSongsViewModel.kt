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
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.download.SongStorage
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems

/**
 * What the Songs tab shows on top of its origins, all off at first. They live with the view
 * model, as long as the tab is in the back stack.
 *
 * @property withDisliked shows the songs the user disliked too
 * @property cachedOnly only the songs with a whole copy in the cache
 * @property downloadedOnly only the songs the user downloaded; the sheet never turns it on with
 * [cachedOnly]
 */
data class SongFilters(
    val withDisliked: Boolean = false,
    val cachedOnly: Boolean = false,
    val downloadedOnly: Boolean = false,
) {

    /** Whether [song] shows, where [storage] says each song is kept ([SongDownloads.songs]). */
    fun keeps(song: MediaItem, storage: Map<String, SongStorage>): Boolean {
        if (!cachedOnly && !downloadedOnly) return true
        val kept = song.origin?.let { storage[SongDownloads.key(it, song.mediaId)] }
        return (cachedOnly && kept == SongStorage.CACHED)
                || (downloadedOnly && kept == SongStorage.DOWNLOADED)
    }
}

/**
 * The songs in the active user's library, of the origins the filter shows. Pages are kept while
 * the screen lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeSongsViewModel(
    private val songRepository: UserSongRepository,
    private val openSubsonicLibrary: OpenSubsonicLibrary,
    private val youtubeLibrary: YouTubeLibrary,
    private val songDownloads: SongDownloads,
) : ViewModel() {

    private val _filters = MutableStateFlow(SongFilters())

    val filter = OriginFilter(
        viewModelScope,
        Preferences.HOME_SONGS_ORIGINS,
        stored = songRepository.origins(),
    )

    val filters: StateFlow<SongFilters> = _filters.asStateFlow()

    val songs: Flow<PagingData<MediaItem>> =
        combine(filter.selected, _filters.map { it.withDisliked }.distinctUntilChanged(), ::Pair)
            .flatMapLatest { (origins, withDisliked) ->
                songRepository.all(origins = origins, withDisliked = withDisliked)
            }
            .toMediaItems()
            .cachedIn(viewModelScope)
            // The cache isn't in the database, its songs are picked from the pages
            .let { pages ->
                combine(pages, _filters, songDownloads.songs) { page, filters, storage ->
                    if (!filters.cachedOnly && !filters.downloadedOnly) page
                    else page.filter { filters.keeps(it, storage) }
                }
            }

    /**
     * Brings the songs liked on the server and on YouTube into the library, call it when the tab
     * shows. Each does nothing while it isn't set up.
     */
    fun refresh() {
        openSubsonicLibrary.refresh()
        youtubeLibrary.refresh()
    }

    /** Changes what the tab shows on top of its origins, at once. */
    fun filter(transform: (SongFilters) -> SongFilters) = _filters.update(transform)

    /** Every song of the list right now, in its order, for the quick actions. */
    suspend fun allSongs(): List<MediaItem> =
        // Every song of a large library takes long to turn into items, never on the main thread
        withContext(Dispatchers.Default) {
            val filters = _filters.value
            val storage = songDownloads.songs.value
            songRepository
                .list(origins = filter.selected.value, withDisliked = filters.withDisliked)
                .mapNotNull { it.toMediaItem() }
                .filter { filters.keeps(it, storage) }
        }
}
