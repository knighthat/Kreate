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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.PeriodPlayRow
import me.knighthat.kreate3.database.dao.PeriodSummaryRow
import me.knighthat.kreate3.database.dao.PlaybackHistoryDao
import me.knighthat.kreate3.database.dao.SearchHistoryDao
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SearchHistoryEntity
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.PeriodPlay
import me.knighthat.kreate3.database.model.PeriodSummary
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.paging.LARGE_PAGE_SIZE
import me.knighthat.kreate3.database.paging.SearchSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import me.knighthat.kreate3.model.Origin
import java.text.Normalizer
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

    override suspend fun recordSearch(query: String) {
        val search = query.normalized()
        val userId = scope.requireId()
        database.inTransaction {
            // The same search saved before searches were normalized
            searchHistoryDao.queries(userId)
                .filter { it != search && it.normalized() == search }
                .forEach { searchHistoryDao.delete(userId, it) }
            searchHistoryDao.upsert(SearchHistoryEntity(search, userId, clock.now()))
        }
    }

    override suspend fun deleteSearch(query: String) =
        searchHistoryDao.delete(scope.requireId(), query)

    override suspend fun clearSearches() = searchHistoryDao.clear(scope.requireId())

    override fun recentPlays(limit: Int): Flow<List<PlaybackHistory>> =
        scope.observe(emptyList()) { userId ->
            playbackHistoryDao.recent(userId, limit).map { it.map(PlaybackHistoryEntity::toModel) }
        }

    override fun plays(
        periods: List<PlayPeriod>,
        origins: Set<Origin>,
        newestFirst: Boolean,
    ): Flow<PagingData<PeriodPlay>> =
        if (periods.isEmpty()) flowOf(PagingData.empty())
        else scope.observe(PagingData.empty()) { userId ->
            database.pagedFlow(
                PeriodPlayRow::toModel,
                "playback_history",
                "user_song_view",
                pageSize = LARGE_PAGE_SIZE,
            ) {
                playbackHistoryDao.pagedPeriodPlays(
                    periodPlays(
                        userId,
                        periods,
                        origins,
                        newestFirst
                    )
                )
            }
        }

    override suspend fun listPlays(
        periods: List<PlayPeriod>,
        origins: Set<Origin>,
        newestFirst: Boolean,
    ): List<PeriodPlay> =
        if (periods.isEmpty()) emptyList()
        else playbackHistoryDao
            .listPeriodPlays(periodPlays(scope.awaitId(), periods, origins, newestFirst))
            .map(PeriodPlayRow::toModel)

    override fun periodSummaries(
        periods: List<PlayPeriod>,
        origins: Set<Origin>,
    ): Flow<List<PeriodSummary>> =
        if (periods.isEmpty()) flowOf(emptyList())
        else scope.observe(emptyList()) { userId ->
            playbackHistoryDao.observeSummaries(
                RoomRawQuery(
                    sql = "SELECT p.period AS period, COUNT(*) AS plays, " +
                            "COUNT(DISTINCT h.song_id) AS songs, " +
                            "SUM(h.time_spent) AS time_spent " +
                            "${playsIn(periods, origins)} GROUP BY p.period",
                    onBindStatement = { it.bindText(1, userId) },
                )
            ).map { rows -> rows.map(PeriodSummaryRow::toModel) }
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

    override suspend fun deletePlay(id: Long) =
        playbackHistoryDao.delete(scope.requireId(), listOf(id))

    override suspend fun deletePlays(periods: List<PlayPeriod>, origins: Set<Origin>) {
        if (periods.isEmpty()) return
        val userId = scope.requireId()
        database.inTransaction {
            playbackHistoryDao.listIds(
                RoomRawQuery(
                    sql = "SELECT h.id ${playsIn(periods, origins)}",
                    onBindStatement = { it.bindText(1, userId) },
                )
            ).chunked(MAX_IDS).forEach { playbackHistoryDao.delete(userId, it) }
        }
    }

    override suspend fun clearPlays() = playbackHistoryDao.clear(scope.requireId())

    /** Trimmed, with accents composed the same whatever the keyboard. */
    private fun String.normalized(): String = Normalizer.normalize(trim(), Normalizer.Form.NFC)

    /**
     * The plays in [periods] with their songs, newest period and play first unless
     * [newestFirst]. Its `?1` is the user.
     */
    private fun periodPlays(
        userId: String,
        periods: List<PlayPeriod>,
        origins: Set<Origin>,
        newestFirst: Boolean,
    ): RoomRawQuery {
        val direction = if (newestFirst) "DESC" else "ASC"
        return RoomRawQuery(
            sql = "SELECT v.*, p.period AS period, h.id AS play_id, h.created_at AS played_at " +
                    "${playsIn(periods, origins)} " +
                    "ORDER BY p.period ${if (newestFirst) "ASC" else "DESC"}, " +
                    "h.created_at $direction, h.id $direction",
            onBindStatement = { it.bindText(1, userId) },
        )
    }

    private companion object {

        // A like is a time, a dislike is negative
        const val NOT_DISLIKED = "AND (v.liked_at IS NULL OR v.liked_at >= 0)"

        /** Ids bound in one statement at most, under SQLite's limit of variables. */
        const val MAX_IDS = 500

        /**
         * `FROM` the plays of the user `?1` in [periods] (aliased `p`, with `period`, `start`,
         * `stop`) of songs from [origins] that the library keeps and aren't disliked, aliased
         * `h`. The periods are numbers written into the query, never the user's input.
         */
        fun playsIn(periods: List<PlayPeriod>, origins: Set<Origin>): String {
            val spans = periods.withIndex().joinToString(" UNION ALL ") { (index, period) ->
                "SELECT $index AS period, ${period.start.toEpochMilliseconds()} AS start, " +
                        "${period.end.toEpochMilliseconds()} AS stop"
            }
            val origin = originFilter(origins).replace("AND origin", "AND v.origin")
            return "FROM playback_history h " +
                    "JOIN ($spans) p ON h.created_at >= p.start AND h.created_at < p.stop " +
                    "JOIN user_song_view v ON v.user_id = h.user_id AND v.song_id = h.song_id " +
                    "WHERE h.user_id = ?1 $NOT_DISLIKED $origin"
        }
    }
}
