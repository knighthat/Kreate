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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val PAGE_SIZE = 50

/**
 * - A page is small, so the first rows show quickly and a row costs little to load.
 * - Pages far behind the reader are dropped ([PagingConfig.maxSize]), memory stays flat
 *   however long the list is.
 * - No placeholders, a list of any length is not counted row by row.
 */
private val CONFIG = PagingConfig(
    pageSize = PAGE_SIZE,
    prefetchDistance = PAGE_SIZE,
    initialLoadSize = PAGE_SIZE * 2,
    enablePlaceholders = false,
    maxSize = PAGE_SIZE * 10,
)

/**
 * Loads [source] page by page and turns every row with [transform]. A new source is made
 * each time the data changes, so the list always follows the database.
 */
internal fun <E : Any, M : Any> pagedFlow(
    transform: (E) -> M,
    source: () -> PagingSource<Int, E>,
): Flow<PagingData<M>> =
    Pager(CONFIG, pagingSourceFactory = source).flow.map { page -> page.map { transform(it) } }
