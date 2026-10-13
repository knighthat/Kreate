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

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.entity.SongFormatEntity
import me.knighthat.kreate.database.model.Origin

@Dao
internal interface SongFormatDao {

    @Query("SELECT * FROM song_format WHERE song_id = :songId")
    fun formatsOf(songId: String): Flow<List<SongFormatEntity>>

    @Query("SELECT * FROM song_format WHERE song_id = :songId AND tag = :tag")
    suspend fun get(songId: String, tag: String): SongFormatEntity?

    /**
     * Ids of the available songs from [origin] without a format [tag] that has a gain.
     */
    @Query(
        "SELECT id FROM song WHERE origin = :origin AND unavailable_since IS NULL AND id NOT IN " +
                "(SELECT song_id FROM song_format WHERE tag = :tag AND gain IS NOT NULL)"
    )
    suspend fun idsWithoutGain(origin: Origin, tag: String): List<String>

    /**
     * Size in bytes of the formats [tag] that have a gain and a known size, by song.
     */
    @Query("SELECT song_id, length FROM song_format WHERE tag = :tag AND gain IS NOT NULL AND length IS NOT NULL")
    suspend fun lengths(tag: String): List<FormatLength>

    @Upsert
    suspend fun upsert(format: SongFormatEntity)

    @Query("DELETE FROM song_format WHERE song_id = :songId")
    suspend fun deleteAll(songId: String)
}

internal data class FormatLength(
    @ColumnInfo(name = "song_id")
    val songId: String,
    val length: Long,
)
