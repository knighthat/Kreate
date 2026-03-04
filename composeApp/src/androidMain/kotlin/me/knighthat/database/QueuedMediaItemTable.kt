package me.knighthat.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.PersistentQueue

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable {

    @Query("""
        SELECT *
        FROM persistent_queue q
        JOIN songs s ON s.id = q.song_id 
        LIMIT :limit
    """)
    suspend fun allBlocking( limit: Int = Int.MAX_VALUE ): List<PersistentQueue.Item>

    /**
     * Attempt to write the list of [PersistentQueue] to database.
     *
     * ### Standalone use
     *
     * When **1** element fails, the entire list is
     * considered failed, database rolls back its operation,
     * and passes exception to caller.
     *
     * ### Transaction use
     *
     * When **1** element fails, the entire list is
     * considered failed, **the entire transaction rolls back**
     * and passes exception to caller.
     *
     * @param queuedMediaItems list of [PersistentQueue] to insert to database
     */
    @Insert
    @Throws(SQLException::class)
    fun insert( queuedMediaItems: List<PersistentQueue> )

    @Query("DELETE FROM persistent_queue")
    fun deleteAll(): Int
}