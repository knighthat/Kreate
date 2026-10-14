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

import androidx.media3.common.MediaItem
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * Every item of a page of YouTube, like the list behind "See more" of an artist's row, each with
 * the [Tab] of its kind. Fetched part after part as the list scrolls, never stored.
 */
fun browseYouTube(
    provider: YouTubeProvider,
    endpoint: YouTubeBrowse
): Flow<PagingData<Pair<Tab, MediaItem>>> =
    Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
        YouTubeBrowsePagingSource(provider, endpoint)
    }.flow

/** Items YouTube gives per request, about. */
private const val PAGE_SIZE = 20

/**
 * Keys are the continuations, `null` for the first part. An item given twice is shown once.
 */
private class YouTubeBrowsePagingSource(
    private val provider: YouTubeProvider,
    private val endpoint: YouTubeBrowse,
) : PagingSource<String, Pair<Tab, MediaItem>>() {

    private val shown = mutableSetOf<String>()

    // A page can't start in the middle, it starts over
    override fun getRefreshKey(state: PagingState<String, Pair<Tab, MediaItem>>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Pair<Tab, MediaItem>> {
        if (params.key == null) shown.clear()

        val page = try {
            provider.browse(endpoint, params.key)
        } catch (e: YouTubeException) {
            return LoadResult.Error(e)
        }
        val items = page.items
            .map { it.tab to it.toMediaItem(provider) }
            .filter { (tab, item) -> shown.add("${tab.name}:${item.mediaId}") }
        return LoadResult.Page(items, prevKey = null, nextKey = page.continuation)
    }
}
