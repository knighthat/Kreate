package me.knighthat.database

import androidx.room.Dao
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

    @Query("DELETE FROM QueuedMediaItem")
    fun deleteAll(): Int
}