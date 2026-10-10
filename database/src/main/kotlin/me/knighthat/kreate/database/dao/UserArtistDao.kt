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
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.entity.UserArtistEntity

@Dao
internal interface UserArtistDao {

    @Query("SELECT * FROM user_artist WHERE user_id = :userId")
    fun all(userId: String): Flow<List<UserArtistEntity>>

    @Query("SELECT * FROM user_artist WHERE user_id = :userId AND follow_since IS NOT NULL ORDER BY follow_since DESC")
    fun followed(userId: String): Flow<List<UserArtistEntity>>

    @Query("SELECT * FROM user_artist WHERE user_id = :userId AND artist_id = :artistId")
    fun find(userId: String, artistId: String): Flow<UserArtistEntity?>

    @Query("SELECT * FROM user_artist WHERE user_id = :userId AND artist_id = :artistId")
    suspend fun get(userId: String, artistId: String): UserArtistEntity?

    @Upsert
    suspend fun upsert(artist: UserArtistEntity)

    @Query("DELETE FROM user_artist WHERE user_id = :userId AND artist_id = :artistId")
    suspend fun delete(userId: String, artistId: String)
}
