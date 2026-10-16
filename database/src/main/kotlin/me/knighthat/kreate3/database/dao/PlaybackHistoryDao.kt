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
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import kotlin.time.Duration

@Dao
internal interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit")
    fun recent(userId: String, limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Insert
    suspend fun insert(history: PlaybackHistoryEntity): Long

    @Query("UPDATE playback_history SET time_spent = :timeSpent WHERE id = :id")
    suspend fun updateTimeSpent(id: Long, timeSpent: Duration)

    @Query("DELETE FROM playback_history WHERE user_id = :userId")
    suspend fun clear(userId: String)
}
