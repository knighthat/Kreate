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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.entity.UserSongEntity
import me.knighthat.kreate.database.view.UserSongViewEntity

@Dao
internal interface UserSongDao {

    /**
     * One page at a time of the songs in [query], which must select from `user_song_view`.
     */
    @RawQuery(observedEntities = [UserSongViewEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserSongViewEntity>

    @Query("SELECT * FROM user_song_view WHERE user_id = :userId AND song_id = :songId")
    fun find(userId: String, songId: String): Flow<UserSongViewEntity?>

    @Query("SELECT * FROM user_song WHERE user_id = :userId AND song_id = :songId")
    suspend fun get(userId: String, songId: String): UserSongEntity?

    @Upsert
    suspend fun upsert(song: UserSongEntity)

    /**
     * Adds the songs the user doesn't have yet. Songs already there keep their overrides.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<UserSongEntity>)

    @Query("DELETE FROM user_song WHERE user_id = :userId AND song_id = :songId")
    suspend fun delete(userId: String, songId: String)
}
