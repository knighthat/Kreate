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

package me.knighthat.kreate3.database.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.paging.SearchSort
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import kotlin.time.Duration

/**
 * Searches and listening sessions of the active user.
 */
interface HistoryRepository {

    /**
     * Searches, a page at a time. Most recent first unless [order] says otherwise.
     */
    fun searches(
        order: SortOrder<SearchSort> = SortOrder(SearchSort.LAST_USED, SortDirection.DESCENDING),
    ): Flow<PagingData<SearchHistory>>

    /**
     * Up to [limit] searches that start with [prefix] regardless of case, most recent first.
     * Every search matches a blank [prefix].
     */
    fun searchesStartingWith(prefix: String, limit: Int): Flow<List<SearchHistory>>

    /**
     * Remembers [query] trimmed, as typed otherwise: `Queen` and `queen` are two searches, so are
     * `the  queen` and `the queen`. Searching for it again only refreshes the time.
     */
    suspend fun recordSearch(query: String)

    suspend fun deleteSearch(query: String)

    suspend fun clearSearches()

    /**
     * Latest [limit] listening sessions, most recent first.
     */
    fun recentPlays(limit: Int): Flow<List<PlaybackHistory>>

    /**
     * Records that the song was played for [timeSpent]. The song must be saved first.
     *
     * @return the id of the play, for [updatePlay]
     */
    suspend fun recordPlay(songId: String, timeSpent: Duration): Long

    /**
     * Changes the time spent of a play recorded earlier, for a session that went on after it was
     * recorded. The play stays with the user it was recorded for.
     */
    suspend fun updatePlay(id: Long, timeSpent: Duration)

    suspend fun clearPlays()
}
