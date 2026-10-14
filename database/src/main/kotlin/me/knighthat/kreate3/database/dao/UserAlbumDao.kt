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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.UserAlbumEntity
import me.knighthat.kreate3.database.view.UserAlbumViewEntity

@Dao
internal interface UserAlbumDao {

    /**
     * One page at a time of the albums in [query], which must select from `user_album_view`.
     */
    @RawQuery(observedEntities = [UserAlbumViewEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserAlbumViewEntity>

    @Query("SELECT * FROM user_album_view WHERE user_id = :userId AND album_id = :albumId")
    fun find(userId: String, albumId: String): Flow<UserAlbumViewEntity?>

    /**
     * Albums of the library that have a song the artist is credited on, newest first.
     */
    @Query(
        "SELECT * FROM user_album_view WHERE user_id = :userId AND album_id IN (" +
                "SELECT s.album_id FROM song s JOIN song_artist_map m ON m.song_id = s.id " +
                "WHERE m.artist_id = :artistId AND s.album_id IS NOT NULL) " +
                "ORDER BY year DESC, name COLLATE NOCASE LIMIT :limit"
    )
    fun ofArtist(userId: String, artistId: String, limit: Int): Flow<List<UserAlbumViewEntity>>

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
