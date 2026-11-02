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

package me.knighthat.kreate3.source.opensubsonic

import androidx.media3.common.MediaItem
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.Tab

/** Results asked of the server per request. */
private const val PAGE_SIZE = 50

/** Matches of each kind suggested while the user types. */
private const val SUGGESTIONS_PER_KIND = 3

/**
 * The results of searching the server for [query], the items of [tab] only, fetched page after
 * page as the list scrolls. They are shown live and never stored.
 *
 * The API doesn't search playlists, so the user's playlists on the server whose name holds the
 * [query] are listed instead, all at once.
 */
fun searchOpenSubsonic(
    provider: OpenSubsonicProvider,
    query: String,
    tab: Tab,
): Flow<PagingData<MediaItem>> =
    Pager(
        PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE,
            enablePlaceholders = false
        )
    ) {
        OpenSubsonicSearchPagingSource(provider, query, tab)
    }.flow

/**
 * A few matches of each kind for [query], songs first, to suggest while the user types. Playlists
 * aren't searched, see [searchOpenSubsonic].
 *
 * @return nothing while the server is off or not set up
 * @throws ServerException when the server can't be used or refuses the request
 */
suspend fun OpenSubsonicProvider.suggestions(query: String): List<Pair<Tab, MediaItem>> {
    val found = searchLibrary(
        query,
        songs = SUGGESTIONS_PER_KIND,
        albums = SUGGESTIONS_PER_KIND,
        artists = SUGGESTIONS_PER_KIND,
    ) ?: return emptyList()

    return found.songs.map { Tab.SONGS to it.toLiveMediaItem() } +
            found.albums.map { Tab.ALBUMS to it.item.toLiveMediaItem() } +
            found.artists.map { Tab.ARTISTS to it.item.toLiveMediaItem() }
}

/** Pages of the search keyed by how many items came before, nothing while the server is off. */
private class OpenSubsonicSearchPagingSource(
    private val provider: OpenSubsonicProvider,
    private val query: String,
    private val tab: Tab,
) : PagingSource<Int, MediaItem>() {

    // A search can't start in the middle, it starts over
    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? = null

    // Paging loads where the pages are collected, the main thread: the items are made off it
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> =
        withContext(Dispatchers.Default) { loadPage(params) }

    private suspend fun loadPage(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val size = params.loadSize

        val found = try {
            if (tab == Tab.PLAYLISTS)
                playlists().let { Found(it, it.size) }
            else
                page(offset, size)
        } catch (e: ServerException) {
            return LoadResult.Error(e)
        }

        // A short page is the last, counted as the server sent it: an item left out without an
        // id doesn't end the search. The playlists come in one.
        val next =
            if (tab == Tab.PLAYLISTS || found.listed < size) null else offset + found.listed
        return LoadResult.Page(found.items, prevKey = null, nextKey = next)
    }

    /**
     * @property listed how many items the server sent, more than [items] when some had no id
     */
    private class Found(val items: List<MediaItem>, val listed: Int)

    private suspend fun page(offset: Int, size: Int): Found {
        val found = provider.searchLibrary(
            query,
            songs = if (tab == Tab.SONGS) size else 0,
            albums = if (tab == Tab.ALBUMS) size else 0,
            artists = if (tab == Tab.ARTISTS) size else 0,
            offset = offset,
        ) ?: return Found(emptyList(), 0)

        return when (tab) {
            Tab.SONGS -> Found(found.songs.map { it.toLiveMediaItem() }, found.songsListed)
            Tab.ALBUMS -> Found(found.albums.map { it.item.toLiveMediaItem() }, found.albumsListed)
            Tab.ARTISTS ->
                Found(found.artists.map { it.item.toLiveMediaItem() }, found.artistsListed)

            Tab.PLAYLISTS -> Found(emptyList(), 0)
        }
    }

    private suspend fun playlists(): List<MediaItem> =
        provider.libraryPlaylists().orEmpty()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toLiveMediaItem() }
}
