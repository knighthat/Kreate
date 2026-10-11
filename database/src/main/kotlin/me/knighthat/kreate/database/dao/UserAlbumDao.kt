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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.entity.UserAlbumEntity
import me.knighthat.kreate.database.view.UserAlbumViewEntity

@Dao
internal interface UserAlbumDao {

    @Query("SELECT * FROM user_album_view WHERE user_id = :userId")
    fun all(userId: String): Flow<List<UserAlbumViewEntity>>

    @Query("SELECT * FROM user_album_view WHERE user_id = :userId AND bookmarked_at IS NOT NULL ORDER BY bookmarked_at DESC")
    fun bookmarked(userId: String): Flow<List<UserAlbumViewEntity>>

    @Query("SELECT * FROM user_album_view WHERE user_id = :userId AND album_id = :albumId")
    fun find(userId: String, albumId: String): Flow<UserAlbumViewEntity?>

    @Query("SELECT * FROM user_album WHERE user_id = :userId AND album_id = :albumId")
    suspend fun get(userId: String, albumId: String): UserAlbumEntity?

    @Upsert
    suspend fun upsert(album: UserAlbumEntity)

    /**
     * Adds the albums the user doesn't have yet. Albums already there keep their overrides.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(albums: List<UserAlbumEntity>)

    @Query("DELETE FROM user_album WHERE user_id = :userId AND album_id = :albumId")
    suspend fun delete(userId: String, albumId: String)
}
