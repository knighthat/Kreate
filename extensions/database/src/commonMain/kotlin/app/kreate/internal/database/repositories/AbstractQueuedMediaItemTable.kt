package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.ext.PersistentQueueItem
import app.kreate.database.repositories.QueuedMediaItemTable

@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractQueuedMediaItemTable: QueuedMediaItemTable {

    override val tableName: String
        get() = "persistent_queue"

    @Query("""
        SELECT * 
        FROM persistent_queue
        JOIN songs ON song_id = id
        LIMIT :limit
    """)
    abstract override fun blockingItems( limit: Int ): List<PersistentQueueItem>

    @Query("DELETE FROM persistent_queue")
    abstract override fun deleteAll(): Int
}