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
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.PersistentQueueEntity

@Dao
internal abstract class PersistentQueueDao {

    @Query("SELECT * FROM persistent_queue WHERE user_id = :userId ORDER BY ordinal")
    abstract fun queue(userId: String): Flow<List<PersistentQueueEntity>>

    @Query("SELECT * FROM persistent_queue WHERE user_id = :userId ORDER BY ordinal")
    abstract suspend fun list(userId: String): List<PersistentQueueEntity>

    /** Inserts the item if its song is saved, an item of a missing song would break the key. */
    @Query(
        """
        INSERT INTO persistent_queue (user_id, ordinal, song_id, position) 
        SELECT :userId, :ordinal, :songId, :position 
        WHERE EXISTS (
            SELECT 1 
            FROM song 
            WHERE id = :songId
        )
    """
    )
    protected abstract suspend fun insertIfSaved(
        userId: String,
        ordinal: Int,
        songId: String,
        position: Long?,
    )

    /**
     * Gives the item at [ordinal] the [position] and takes it from every other item of [userId],
     * in one statement, so a single item ever has one.
     */
    @Query(
        """
        UPDATE persistent_queue 
        SET position = 
            CASE 
                WHEN ordinal = :ordinal THEN :position 
                ELSE NULL 
            END 
        WHERE user_id = :userId 
        AND (ordinal = :ordinal OR position IS NOT NULL)
    """
    )
    abstract suspend fun setPosition(userId: String, ordinal: Int, position: Long)

    @Query("DELETE FROM persistent_queue WHERE user_id = :userId")
    abstract suspend fun clear(userId: String)

    /**
     * Swaps the whole queue of [userId] in one transaction, a failure keeps the old queue.
     * Items whose song isn't saved are left out, the others keep their ordinal.
     */
    @Transaction
    open suspend fun replace(userId: String, items: List<PersistentQueueEntity>) {
        clear(userId)
        for (item in items)
            insertIfSaved(userId, item.ordinal, item.songId, item.position)
    }
}
