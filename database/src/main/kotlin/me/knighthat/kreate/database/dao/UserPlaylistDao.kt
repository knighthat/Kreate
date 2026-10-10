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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate.database.entity.UserPlaylistEntity

@Dao
internal interface UserPlaylistDao {

    @Query("SELECT * FROM user_playlist WHERE user_id = :userId ORDER BY name")
    fun all(userId: String): Flow<List<UserPlaylistEntity>>

    @Query("SELECT * FROM user_playlist WHERE playlist_id = :playlistId")
    fun find(playlistId: Long): Flow<UserPlaylistEntity?>

    @Query("SELECT * FROM user_playlist WHERE playlist_id = :playlistId")
    suspend fun get(playlistId: Long): UserPlaylistEntity?

    @Query(
        """
        SELECT song.* FROM song
        JOIN song_playlist_map ON song_playlist_map.song_id = song.id
        WHERE song_playlist_map.playlist_id = :playlistId
        """,
    )
    fun songsOf(playlistId: Long): Flow<List<SongEntity>>

    /**
     * @return the row id when a playlist was inserted, `-1` when an existing one was updated
     */
    @Upsert
    suspend fun upsert(playlist: UserPlaylistEntity): Long

    @Query("DELETE FROM user_playlist WHERE playlist_id = :playlistId AND user_id = :userId")
    suspend fun delete(playlistId: Long, userId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(map: SongPlaylistMapEntity)

    @Delete
    suspend fun removeSong(map: SongPlaylistMapEntity)
}
