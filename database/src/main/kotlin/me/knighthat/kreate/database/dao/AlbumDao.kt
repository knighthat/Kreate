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
import me.knighthat.kreate.database.entity.AlbumEntity
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.model.Origin

@Dao
internal interface AlbumDao {

    @Query("SELECT * FROM album WHERE id = :id")
    fun find(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM album WHERE id = :id")
    suspend fun get(id: String): AlbumEntity?

    @Query("SELECT * FROM album WHERE origin = :origin")
    suspend fun withOrigin(origin: Origin): List<AlbumEntity>

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT * FROM song WHERE album_id = :albumId ORDER BY album_position")
    fun songsOf(albumId: String): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsert(album: AlbumEntity)

    @Query("DELETE FROM album WHERE id = :id")
    suspend fun delete(id: String)
}
