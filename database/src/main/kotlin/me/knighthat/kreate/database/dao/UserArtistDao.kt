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
import me.knighthat.kreate.database.entity.UserArtistEntity
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.view.UserArtistViewEntity

@Dao
internal interface UserArtistDao {

    /**
     * One page at a time of the artists in [query], which must select from `user_artist_view`.
     */
    @RawQuery(observedEntities = [UserArtistViewEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserArtistViewEntity>

    @Query("SELECT * FROM user_artist_view WHERE user_id = :userId AND artist_id = :artistId")
    fun find(userId: String, artistId: String): Flow<UserArtistViewEntity?>

    @Query("SELECT * FROM user_artist WHERE user_id = :userId AND artist_id = :artistId")
    suspend fun get(userId: String, artistId: String): UserArtistEntity?

    @Upsert
    suspend fun upsert(artist: UserArtistEntity)

    /**
     * Adds the artists the user doesn't have yet. Artists already there keep their overrides.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(artists: List<UserArtistEntity>)

    /**
     * The user's rows about artists from [origin].
     */
    @Query(
        "SELECT ua.* FROM user_artist ua JOIN artist a ON a.id = ua.artist_id " +
                "WHERE ua.user_id = :userId AND a.origin = :origin"
    )
    suspend fun withOrigin(userId: String, origin: Origin): List<UserArtistEntity>

    @Upsert
    suspend fun upsertAll(artists: List<UserArtistEntity>)

    @Delete
    suspend fun deleteAll(artists: List<UserArtistEntity>)

    @Query("DELETE FROM user_artist WHERE user_id = :userId AND artist_id = :artistId")
    suspend fun delete(userId: String, artistId: String)
}
