package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.PersistentQueue
import app.kreate.database.table.DatabaseTable

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable: DatabaseTable<PersistentQueue> {

    override val tableName: String
        get() = "persistent_queue"

    @Query("""
        SELECT * 
        FROM persistent_queue
        JOIN songs ON song_id = id
        LIMIT :limit
    """)
    fun blockingItems( limit: Int = Int.MAX_VALUE ): List<PersistentQueue.Item>

    @Query("UPDATE persistent_queue SET position = :newPosition WHERE song_id = :songId")
    fun updatePosition( songId: String, newPosition: Long? ): Int

    @Query("DELETE FROM persistent_queue")
    fun deleteAll(): Int
}