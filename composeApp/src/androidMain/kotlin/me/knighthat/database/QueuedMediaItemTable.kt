package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.PersistentQueue
import app.kreate.database.table.DatabaseTable

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable: DatabaseTable<PersistentQueue> {

    @Query("""
        SELECT *
        FROM persistent_queue q
        JOIN songs s ON s.id = q.song_id 
        LIMIT :limit
    """)
    suspend fun allBlocking( limit: Int = Int.MAX_VALUE ): List<PersistentQueue.Item>

    @Query("DELETE FROM persistent_queue")
    fun deleteAll(): Int
}