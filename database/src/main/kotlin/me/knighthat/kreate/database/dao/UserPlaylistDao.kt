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

package me.knighthat.kreate.database.dao

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
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate.database.entity.UserPlaylistEntity
import me.knighthat.kreate.database.model.Origin

@Dao
internal interface UserPlaylistDao {

    /**
     * One page at a time of the playlists in [query], which must select from `user_playlist`.
     */
    @RawQuery(observedEntities = [UserPlaylistEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserPlaylistEntity>

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

    /**
     * The user's playlists from [origin] that have an id there.
     */
    @Query("SELECT * FROM user_playlist WHERE user_id = :userId AND origin = :origin AND remote_id IS NOT NULL")
    suspend fun withRemoteOrigin(userId: String, origin: Origin): List<UserPlaylistEntity>

    @Upsert
    suspend fun upsertAll(playlists: List<UserPlaylistEntity>)

    @Delete
    suspend fun deleteAll(playlists: List<UserPlaylistEntity>)

    @Query("DELETE FROM user_playlist WHERE playlist_id = :playlistId AND user_id = :userId")
    suspend fun delete(playlistId: Long, userId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(map: SongPlaylistMapEntity)

    @Delete
    suspend fun removeSong(map: SongPlaylistMapEntity)
}
