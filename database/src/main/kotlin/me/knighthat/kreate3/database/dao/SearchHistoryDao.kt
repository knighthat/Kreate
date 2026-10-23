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

package me.knighthat.kreate3.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.SearchHistoryEntity

@Dao
internal interface SearchHistoryDao {

    /**
     * One page at a time of the searches in [query], which must select from `search_history`.
     */
    @RawQuery(observedEntities = [SearchHistoryEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, SearchHistoryEntity>

    @Query(
        "SELECT * FROM search_history WHERE user_id = :userId AND query LIKE :pattern ESCAPE '\\' " +
                "ORDER BY last_used DESC NULLS LAST, query LIMIT :limit"
    )
    fun startingWith(userId: String, pattern: String, limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("SELECT query FROM search_history WHERE user_id = :userId")
    suspend fun queries(userId: String): List<String>

    @Upsert
    suspend fun upsert(history: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE user_id = :userId AND query = :query")
    suspend fun delete(userId: String, query: String)

    @Query("DELETE FROM search_history WHERE user_id = :userId")
    suspend fun clear(userId: String)
}
