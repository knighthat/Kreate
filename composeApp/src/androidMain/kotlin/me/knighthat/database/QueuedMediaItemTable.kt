package me.knighthat.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.table.DatabaseTable
import it.fast4x.rimusic.models.QueuedMediaItem
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable: DatabaseTable<QueuedMediaItem> {

    /**
     * @return all records from this table
     */
    @Query("""
        SELECT DISTINCT * 
        FROM QueuedMediaItem
        LIMIT :limit
    """)
    fun all( limit: Int = Int.MAX_VALUE ): Flow<List<QueuedMediaItem>>

    /**
     * Attempt to write the list of [QueuedMediaItem] to database.
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
     * @param queuedMediaItems list of [QueuedMediaItem] to insert to database
     */
    @Insert
    @Throws(SQLException::class)
    fun insert( queuedMediaItems: List<QueuedMediaItem> )

    @Query("DELETE FROM QueuedMediaItem")
    fun deleteAll(): Int
}