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
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.view.UserSongViewEntity
import me.knighthat.kreate3.model.Origin
import kotlin.time.Instant

@Dao
internal interface UserSongDao {

    /**
     * One page at a time of the songs in [query], which must select from `user_song_view`.
     */
    @RawQuery(observedEntities = [UserSongViewEntity::class])
    fun paged(query: RoomRawQuery): PagingSource<Int, UserSongViewEntity>

    @Query("SELECT DISTINCT origin FROM user_song_view WHERE user_id = :userId")
    fun origins(userId: String): Flow<List<Origin>>

    /**
     * Every song in [query] at once, which must select from `user_song_view`.
     */
    @RawQuery
    suspend fun list(query: RoomRawQuery): List<UserSongViewEntity>

    @Query("SELECT * FROM user_song_view WHERE user_id = :userId AND song_id = :songId")
    fun find(userId: String, songId: String): Flow<UserSongViewEntity?>

    @Query("SELECT * FROM user_song WHERE user_id = :userId AND song_id = :songId")
    suspend fun get(userId: String, songId: String): UserSongEntity?

    /** Ids of the songs the user disliked, a `liked_at` of -1. */
    @Query("SELECT song_id FROM user_song WHERE user_id = :userId AND liked_at = -1")
    fun dislikedIds(userId: String): Flow<List<String>>

    /** [dislikedIds] right now. */
    @Query("SELECT song_id FROM user_song WHERE user_id = :userId AND liked_at = -1")
    suspend fun listDislikedIds(userId: String): List<String>

    @Upsert
    suspend fun upsert(song: UserSongEntity)

    /**
     * Adds the songs the user doesn't have yet. Songs already there keep their overrides.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<UserSongEntity>)

    /**
     * The user's rows about songs from [origin].
     */
    @Query(
        "SELECT us.* FROM user_song us JOIN song s ON s.id = us.song_id " +
                "WHERE us.user_id = :userId AND s.origin = :origin"
    )
    suspend fun withOrigin(userId: String, origin: Origin): List<UserSongEntity>

    @Upsert
    suspend fun upsertAll(songs: List<UserSongEntity>)

    /**
     * The user's rows about songs on the album [albumId].
     */
    @Query(
        "SELECT us.* FROM user_song us JOIN song s ON s.id = us.song_id " +
                "WHERE us.user_id = :userId AND s.album_id = :albumId"
    )
    suspend fun onAlbum(userId: String, albumId: String): List<UserSongEntity>

    /**
     * Takes the user's own album positions off the songs of the album [albumId].
     */
    @Query(
        "UPDATE user_song SET album_position = NULL, updated_at = :now " +
                "WHERE user_id = :userId AND album_position IS NOT NULL " +
                "AND song_id IN (SELECT id FROM song WHERE album_id = :albumId)"
    )
    suspend fun clearAlbumPositions(userId: String, albumId: String, now: Instant)

    @Query("DELETE FROM user_song WHERE user_id = :userId AND song_id = :songId")
    suspend fun delete(userId: String, songId: String)
}
