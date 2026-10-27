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
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.AlbumEntity
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.UserAlbumEntity
import me.knighthat.kreate3.database.entity.UserArtistEntity
import me.knighthat.kreate3.database.view.UserAlbumViewEntity
import me.knighthat.kreate3.database.view.UserArtistViewEntity
import me.knighthat.kreate3.database.view.UserSongViewEntity
import me.knighthat.kreate3.model.Origin
import kotlin.time.Duration

@Dao
internal interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit")
    fun recent(userId: String, limit: Int): Flow<List<PlaybackHistoryEntity>>

    // The rows below are filtered by the songs' view, which changes with likes and the library

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class])
    fun pagedPeriodPlays(query: RoomRawQuery): PagingSource<Int, PeriodPlayRow>

    @RawQuery
    suspend fun listPeriodPlays(query: RoomRawQuery): List<PeriodPlayRow>

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class])
    fun observeSummaries(query: RoomRawQuery): Flow<List<PeriodSummaryRow>>

    @Query("SELECT * FROM playback_history WHERE user_id = :userId AND song_id = :songId ORDER BY created_at")
    fun songPlays(userId: String, songId: String): Flow<List<PlaybackHistoryEntity>>

    // Statistics, see `StatisticsRepositoryImpl`

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class, SongArtistMapEntity::class])
    fun observeListeningSummary(query: RoomRawQuery): Flow<ListeningSummaryRow>

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class])
    fun observeTopSongs(query: RoomRawQuery): Flow<List<TopSongRow>>

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class, AlbumEntity::class, UserAlbumEntity::class])
    fun observeTopAlbums(query: RoomRawQuery): Flow<List<TopAlbumRow>>

    @RawQuery(
        observedEntities = [
            PlaybackHistoryEntity::class, UserSongViewEntity::class, SongArtistMapEntity::class,
            ArtistEntity::class, UserArtistEntity::class,
        ]
    )
    fun observeTopArtists(query: RoomRawQuery): Flow<List<TopArtistRow>>

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class])
    fun observeOriginTimes(query: RoomRawQuery): Flow<List<OriginTimeRow>>

    @RawQuery(observedEntities = [PlaybackHistoryEntity::class, UserSongViewEntity::class, SongArtistMapEntity::class])
    fun observePlays(query: RoomRawQuery): Flow<List<PlaybackHistoryEntity>>

    @RawQuery
    suspend fun listIds(query: RoomRawQuery): List<Long>

    @Insert
    suspend fun insert(history: PlaybackHistoryEntity): Long

    @Query("UPDATE playback_history SET time_spent = :timeSpent WHERE id = :id")
    suspend fun updateTimeSpent(id: Long, timeSpent: Duration)

    @Query("DELETE FROM playback_history WHERE user_id = :userId AND id IN (:ids)")
    suspend fun delete(userId: String, ids: List<Long>)

    @Query("DELETE FROM playback_history WHERE user_id = :userId")
    suspend fun clear(userId: String)
}

/**
 * A play in a period with its song, see `HistoryRepository.plays`.
 *
 * @property playedAt epoch millis
 */
internal data class PeriodPlayRow(
    @Embedded val song: UserSongViewEntity,
    val period: Int,
    @ColumnInfo(name = "play_id")
    val playId: Long,
    @ColumnInfo(name = "played_at")
    val playedAt: Long,
)

/** @property timeSpent seconds, like `playback_history.time_spent` */
internal data class PeriodSummaryRow(
    val period: Int,
    val plays: Int,
    val songs: Int,
    @ColumnInfo(name = "time_spent")
    val timeSpent: Long,
)

/** @property timeSpent seconds */
internal data class ListeningSummaryRow(
    val plays: Int,
    val songs: Int,
    val artists: Int,
    @ColumnInfo(name = "time_spent")
    val timeSpent: Long,
)

/** @property timeSpent seconds */
internal data class TopSongRow(
    @Embedded val song: UserSongViewEntity,
    val listens: Double,
    @ColumnInfo(name = "time_spent")
    val timeSpent: Long,
)

/** @property timeSpent seconds */
internal data class TopAlbumRow(
    @Embedded val album: UserAlbumViewEntity,
    val listens: Double,
    @ColumnInfo(name = "time_spent")
    val timeSpent: Long,
)

/** @property timeSpent seconds */
internal data class TopArtistRow(
    @Embedded val artist: UserArtistViewEntity,
    val listens: Double,
    @ColumnInfo(name = "time_spent")
    val timeSpent: Long,
)

/** @property timeSpent seconds */
internal data class OriginTimeRow(
    val origin: Origin,
    @ColumnInfo(name = "time_spent")
    val timeSpent: Long,
)
