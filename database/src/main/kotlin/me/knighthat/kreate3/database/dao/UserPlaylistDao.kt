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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.model.Origin

@Dao
internal interface UserPlaylistDao {

    /**
     * One page at a time of the playlists in [query], which must select from `user_playlist`.
     */
    @RawQuery(observedEntities = [UserPlaylistEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserPlaylistEntity>

    @Query("SELECT DISTINCT origin FROM user_playlist WHERE user_id = :userId")
    fun origins(userId: String): Flow<List<Origin>>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(map: SongPlaylistMapEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongs(maps: List<SongPlaylistMapEntity>)

    @Query("DELETE FROM song_playlist_map WHERE playlist_id = :playlistId")
    suspend fun clearSongs(playlistId: Long)

    @Delete
    suspend fun removeSong(map: SongPlaylistMapEntity)
}
