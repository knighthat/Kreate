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
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.view.UserSongViewEntity
import me.knighthat.kreate3.model.Origin

/**
 * A playlist with the covers of its songs, see [UserPlaylistDao.pagedWithCovers].
 *
 * @property covers `[[song_id, origin, thumbnail], …]` as JSON, `null` when it has none
 */
internal data class UserPlaylistRow(
    @Embedded val playlist: UserPlaylistEntity,
    val covers: String?,
)

@Dao
internal interface UserPlaylistDao {

    /**
     * One page at a time of the playlists in [query], which must select from `user_playlist`.
     */
    @RawQuery(observedEntities = [UserPlaylistEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserPlaylistEntity>

    /**
     * Like [paged], each playlist with the covers of its songs. [query] must select from
     * `user_playlist` and add a `covers` column.
     */
    @RawQuery(
        observedEntities = [
            UserPlaylistEntity::class,
            SongPlaylistMapEntity::class,
            PlaybackHistoryEntity::class,
            UserSongViewEntity::class,
        ]
    )
    fun pagedWithCovers(query: RoomRawQuery): PagingSource<Int, UserPlaylistRow>

    @Query("SELECT DISTINCT origin FROM user_playlist WHERE user_id = :userId")
    fun origins(userId: String): Flow<List<Origin>>

    /**
     * Every playlist in [query] at once, which must select from `user_playlist`.
     */
    @RawQuery
    suspend fun list(query: RoomRawQuery): List<UserPlaylistEntity>

    /**
     * The first playlist of [query], which must select from `user_playlist`.
     */
    @RawQuery
    suspend fun first(query: RoomRawQuery): UserPlaylistEntity?

    /**
     * The number [query] counts, which must select `COUNT(*)`.
     */
    @RawQuery
    suspend fun count(query: RoomRawQuery): Int

    @Query("SELECT * FROM user_playlist WHERE playlist_id = :playlistId")
    fun find(playlistId: Long): Flow<UserPlaylistEntity?>

    @Query("SELECT * FROM user_playlist WHERE playlist_id = :playlistId")
    suspend fun get(playlistId: Long): UserPlaylistEntity?

    /**
     * One page at a time of the songs in [query], which must select from `song` and `song_playlist_map`.
     */
    @RawQuery(observedEntities = [SongEntity::class, SongPlaylistMapEntity::class])
    fun pagedSongs(query: RoomRawQuery): PagingSource<Int, SongEntity>

    /**
     * @return the row id when a playlist was inserted, `-1` when an existing one was updated
     */
    @Upsert
    suspend fun upsert(playlist: UserPlaylistEntity): Long

    @Query("DELETE FROM user_playlist WHERE playlist_id = :playlistId AND user_id = :userId")
    suspend fun delete(playlistId: Long, userId: String)

    /** @return the row id when the song was added, `-1` when it was in the playlist already */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(map: SongPlaylistMapEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongs(maps: List<SongPlaylistMapEntity>)

    @Query("DELETE FROM song_playlist_map WHERE playlist_id = :playlistId")
    suspend fun clearSongs(playlistId: Long)

    @Query("DELETE FROM song_playlist_map WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSong(playlistId: Long, songId: String)

    /** The songs of the playlist in its order. */
    @Query("SELECT * FROM song_playlist_map WHERE playlist_id = :playlistId ORDER BY position, song_id")
    suspend fun songLinks(playlistId: Long): List<SongPlaylistMapEntity>

    /** The position after the last song of the playlist, `0` when it has none. */
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM song_playlist_map WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Update
    suspend fun updateSongs(maps: List<SongPlaylistMapEntity>)
}
