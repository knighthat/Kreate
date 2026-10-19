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
import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.PlaybackHistoryDao
import me.knighthat.kreate3.database.dao.SearchHistoryDao
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SearchHistoryEntity
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.paging.SearchSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Room backed implementation of [HistoryRepository].
 */
internal class HistoryRepositoryImpl(
    private val database: AppDatabase,
    private val searchHistoryDao: SearchHistoryDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : HistoryRepository {

    private val scope = UserScope(activeUser)

    override fun searches(order: SortOrder<SearchSort>): Flow<PagingData<SearchHistory>> =
        scope.observe(PagingData.empty()) { userId ->
            database.pagedFlow(SearchHistoryEntity::toModel, "search_history") {
                searchHistoryDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM search_history WHERE user_id = ? ${order.toOrderBy("query")}",
                        onBindStatement = { it.bindText(1, userId) },
                    )
                )
            }
        }

    override fun searchesStartingWith(prefix: String, limit: Int): Flow<List<SearchHistory>> =
        scope.observe(emptyList()) { userId ->
            searchHistoryDao.startingWith(userId, prefixPattern(prefix), limit)
                .map { it.map(SearchHistoryEntity::toModel) }
        }

    override suspend fun recordSearch(query: String) =
        searchHistoryDao.upsert(SearchHistoryEntity(query, scope.requireId(), clock.now()))

    override suspend fun deleteSearch(query: String) =
        searchHistoryDao.delete(scope.requireId(), query)

    override suspend fun clearSearches() = searchHistoryDao.clear(scope.requireId())

    override fun recentPlays(limit: Int): Flow<List<PlaybackHistory>> =
        scope.observe(emptyList()) { userId ->
            playbackHistoryDao.recent(userId, limit).map { it.map(PlaybackHistoryEntity::toModel) }
        }

    override suspend fun recordPlay(songId: String, timeSpent: Duration): Long =
        playbackHistoryDao.insert(
            PlaybackHistoryEntity(
                0,
                songId,
                scope.requireId(),
                timeSpent,
                clock.now()
            )
        )

    override suspend fun updatePlay(id: Long, timeSpent: Duration) =
        playbackHistoryDao.updateTimeSpent(id, timeSpent)

    override suspend fun clearPlays() = playbackHistoryDao.clear(scope.requireId())
}
