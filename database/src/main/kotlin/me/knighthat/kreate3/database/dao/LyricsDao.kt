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
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.LyricsEntity
import me.knighthat.kreate3.model.LyricsProvider

@Dao
internal interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE song_id = :songId AND source = :source")
    fun find(songId: String, source: LyricsProvider): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE song_id = :songId AND source = :source")
    suspend fun get(songId: String, source: LyricsProvider): LyricsEntity?

    @Upsert
    suspend fun upsert(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE song_id = :songId")
    suspend fun delete(songId: String)

    /** The bytes the text of every stored lyrics takes, in UTF-8. */
    @Query("SELECT COALESCE(SUM(LENGTH(CAST(lyrics AS BLOB))), 0) FROM lyrics")
    fun bytes(): Flow<Long>
}
