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
package me.knighthat.kreate3.source.youtube

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeItem
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeSearchFilter
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.Tab

/** Edge in pixels a song's picture is asked at, it shows small in a row. */
const val SONG_ARTWORK = 226

/** Edge in pixels the picture of an album, artist or playlist is asked at, they show in a grid. */
const val CARD_ARTWORK = 544

/** Edge in pixels the large picture on top of a detail screen is asked at. */
const val HEADER_ARTWORK = 720

/** The picture YouTube gave at [uri], at [size]. */
fun YouTubeProvider.artwork(uri: Uri?, size: Int): Uri? =
    uri?.let { artworkUri(it.toString(), size) }

/**
 * The item as a [MediaItem] to show, with its picture at the size it is shown. Nothing is stored.
 */
fun YouTubeItem.toMediaItem(provider: YouTubeProvider): MediaItem {
    fun Uri?.sized(size: Int): Uri? = provider.artwork(this, size)

    return when (this) {
        is YouTubeItem.SongItem ->
            song.toLiveMediaItem((song.song.thumbnail ?: song.album?.thumbnail).sized(SONG_ARTWORK))

        is YouTubeItem.AlbumItem -> album.toLiveMediaItem(album.thumbnail.sized(CARD_ARTWORK))
        is YouTubeItem.ArtistItem -> artist.toLiveMediaItem(artist.thumbnail.sized(CARD_ARTWORK))
        is YouTubeItem.PlaylistItem -> playlist.toLiveMediaItem(
            playlist.thumbnail.sized(
                CARD_ARTWORK
            )
        )
    }
}

/** The kind of item, as the tab that lists it. */
val YouTubeItem.tab: Tab
    get() = when (this) {
        is YouTubeItem.SongItem -> Tab.SONGS
        is YouTubeItem.AlbumItem -> Tab.ALBUMS
        is YouTubeItem.ArtistItem -> Tab.ARTISTS
        is YouTubeItem.PlaylistItem -> Tab.PLAYLISTS
    }

/** What YouTube is searched with for the items of [tab], one after the other. */
private fun filtersOf(tab: Tab): List<YouTubeSearchFilter> = when (tab) {
    Tab.SONGS -> listOf(YouTubeSearchFilter.SONGS)
    Tab.ALBUMS -> listOf(YouTubeSearchFilter.ALBUMS)
    Tab.ARTISTS -> listOf(YouTubeSearchFilter.ARTISTS)
    // YouTube Music's own playlists first, then the ones of users
    Tab.PLAYLISTS -> listOf(
        YouTubeSearchFilter.FEATURED_PLAYLISTS,
        YouTubeSearchFilter.COMMUNITY_PLAYLISTS,
    )
}

/**
 * The results of searching YouTube for [query], the items of [tab] only, fetched page after page
 * as the list scrolls. They are shown live and never stored.
 */
fun searchYouTube(provider: YouTubeProvider, query: String, tab: Tab): Flow<PagingData<MediaItem>> =
    Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
        YouTubeSearchPagingSource(provider, query, tab)
    }.flow

/** Results YouTube gives per request. */
private const val PAGE_SIZE = 20

/**
 * Searches [filtersOf] the [tab] in turn, each until YouTube has no more. An item given twice,
 * which YouTube does across pages, is shown once.
 */
private class YouTubeSearchPagingSource(
    private val provider: YouTubeProvider,
    private val query: String,
    private val tab: Tab,
) : PagingSource<YouTubeSearchPagingSource.Key, MediaItem>() {

    /**
     * @property filter index of the filter in [filters]
     * @property continuation of its previous page, `null` for its first
     */
    data class Key(val filter: Int, val continuation: String?)

    private val filters = filtersOf(tab)

    private val shown = mutableSetOf<String>()

    // A search can't start in the middle, it starts over
    override fun getRefreshKey(state: PagingState<Key, MediaItem>): Key? = null

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, MediaItem> {
        val key = params.key ?: Key(0, null)
        if (key.filter == 0 && key.continuation == null) shown.clear()

        val page = try {
            provider.search(query, filters[key.filter], key.continuation)
        } catch (e: YouTubeException) {
            return LoadResult.Error(e)
        }

        val items = page.items
            .filter { it.tab == tab }
            .map { it.toMediaItem(provider) }
            .filter { shown.add(it.mediaId) }
        val next = when {
            page.continuation != null -> Key(key.filter, page.continuation)
            key.filter + 1 < filters.size -> Key(key.filter + 1, null)
            else -> null
        }
        return LoadResult.Page(items, prevKey = null, nextKey = next)
    }
}
