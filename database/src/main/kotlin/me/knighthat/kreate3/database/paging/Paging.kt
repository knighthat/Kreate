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

package me.knighthat.kreate3.database.paging

import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

/**
 * - A page is small, so the first rows show quickly and a row costs little to load.
 * - Pages far behind the reader are dropped ([PagingConfig.maxSize]), memory stays flat
 *   however long the list is.
 * - No placeholders, a list of any length is not counted row by row.
 */
private fun config(pageSize: Int) = PagingConfig(
    pageSize = pageSize,
    prefetchDistance = pageSize,
    initialLoadSize = pageSize * 2,
    enablePlaceholders = false,
    maxSize = pageSize * 10,
)

/** Pages of lists whose rows cost more to load one by one than in bulk, like grouped plays. */
internal const val LARGE_PAGE_SIZE = PAGE_SIZE * 4

/**
 * Loads [source] page by page and turns every row with [transform]. A new source is made
 * each time [tables] change, so the list always follows the database.
 *
 * Room's sources do that themselves, but miss a change made while their first page loads: they
 * act on changes only once it is done, and that page may be read from before the change. Like
 * the songs a scan adds as the app opens, the list kept showing the library without them. So
 * the sources are also dropped here on every change.
 *
 * @param tables the tables or views [source] reads
 * @param pageSize rows of a page, [LARGE_PAGE_SIZE] for larger ones than usual
 */
internal fun <E : Any, M : Any> RoomDatabase.pagedFlow(
    transform: (E) -> M,
    vararg tables: String,
    pageSize: Int = PAGE_SIZE,
    source: () -> PagingSource<Int, E>,
): Flow<PagingData<M>> {
    val sources = InvalidatingPagingSourceFactory(source)
    val pages = Pager(config(pageSize), pagingSourceFactory = sources).flow
        .map { page -> page.map { transform(it) } }

    return channelFlow {
        launch {
            invalidationTracker.createFlow(*tables, emitInitialState = false)
                .collect { sources.invalidate() }
        }
        pages.collect { send(it) }
    }
}
