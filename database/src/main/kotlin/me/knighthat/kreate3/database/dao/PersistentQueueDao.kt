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

    /**
     * Moves every item of [userId] at [from] or after by [delta] places. Two statements, since
     * the key would clash while the items are renumbered one by one: they go through negative
     * ordinals first.
     */
    @Transaction
    protected open suspend fun shift(userId: String, from: Int, delta: Int) {
        park(userId, from, delta)
        unpark(userId)
    }

    @Query(
        """
        UPDATE persistent_queue 
        SET ordinal = -(ordinal + :delta) - 1 
        WHERE user_id = :userId 
        AND ordinal >= :from
    """
    )
    protected abstract suspend fun park(userId: String, from: Int, delta: Int)

    @Query(
        """
        UPDATE persistent_queue 
        SET ordinal = -ordinal - 1 
        WHERE user_id = :userId 
        AND ordinal < 0
    """
    )
    protected abstract suspend fun unpark(userId: String)

    @Query(
        """
        DELETE FROM persistent_queue 
        WHERE user_id = :userId 
        AND ordinal >= :from 
        AND ordinal < :to
    """
    )
    protected abstract suspend fun deleteRange(userId: String, from: Int, to: Int)

    /**
     * Makes room at [index] and puts [items] there, in one transaction. Items whose song isn't
     * saved are left out, the places they would have held stay empty.
     */
    @Transaction
    open suspend fun insert(userId: String, index: Int, items: List<PersistentQueueEntity>) {
        shift(userId, index, items.size)
        for ((offset, item) in items.withIndex())
            insertIfSaved(userId, index + offset, item.songId, item.position)
    }

    /** Takes the items from [index] on, [count] of them, and closes the gap, in one transaction. */
    @Transaction
    open suspend fun remove(userId: String, index: Int, count: Int) {
        deleteRange(userId, index, index + count)
        shift(userId, index + count, -count)
    }

    /**
     * Moves the [count] items from [from] on so that they start at [to] of the queue left after
     * they are taken out, like `Player.moveMediaItems`, in one transaction. The items in between
     * make way for them.
     */
    @Transaction
    open suspend fun move(userId: String, from: Int, count: Int, to: Int) {
        if (from == to || count <= 0) return

        val low = minOf(from, to)
        val high = if (to > from) to + count else from + count
        parkMoved(userId, from, count, to, low, high)
        unpark(userId)
    }

    @Query(
        """
        UPDATE persistent_queue 
        SET ordinal = -(
            CASE 
                WHEN ordinal >= :from AND ordinal < :from + :count THEN ordinal + (:to - :from) 
                WHEN :to > :from THEN ordinal - :count 
                ELSE ordinal + :count 
            END
        ) - 1 
        WHERE user_id = :userId 
        AND ordinal >= :low 
        AND ordinal < :high
    """
    )
    protected abstract suspend fun parkMoved(
        userId: String,
        from: Int,
        count: Int,
        to: Int,
        low: Int,
        high: Int,
    )
}
