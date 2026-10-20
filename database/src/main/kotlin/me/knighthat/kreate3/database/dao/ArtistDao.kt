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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.model.Origin

@Dao
internal interface ArtistDao {

    @Query("SELECT * FROM artist WHERE id = :id")
    fun find(id: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artist WHERE id = :id")
    suspend fun get(id: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE origin = :origin")
    suspend fun withOrigin(origin: Origin): List<ArtistEntity>

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    /** Stores the artists that aren't stored yet, the others are left as they are. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(artists: List<ArtistEntity>)

    @Upsert
    suspend fun upsert(artist: ArtistEntity)

    @Query("DELETE FROM artist WHERE id = :id")
    suspend fun delete(id: String)
}
