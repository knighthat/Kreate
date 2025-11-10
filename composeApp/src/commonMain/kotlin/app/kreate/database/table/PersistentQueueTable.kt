package app.kreate.database.table

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import app.kreate.database.models.PersistentQueue
import app.kreate.database.view.QueueView
import org.jetbrains.annotations.Blocking


@Dao
@RewriteQueriesToDropUnusedColumns
interface PersistentQueueTable: DatabaseTable<PersistentQueue> {

    override val tableName: String
        get() = PersistentQueue::class.simpleName!!

    @Query("DELETE FROM persistent_queue")
    fun deleteAll(): Int

    @Blocking
    @Query("SELECT * FROM queue_view")
    fun blockingGetAllAsSongInQueue(): List<QueueView>

    @Transaction
    suspend fun save( queue: List<PersistentQueue> ) {
        deleteAll()
        insertIgnore( queue )
    }
}