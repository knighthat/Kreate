package app.kreate.database.repositories

import app.kreate.database.ext.PersistentQueueItem
import app.kreate.database.models.PersistentQueue
import app.kreate.database.table.DatabaseTable


interface QueuedMediaItemTable: DatabaseTable<PersistentQueue> {

    fun blockingItems( limit: Int = Int.MAX_VALUE ): List<PersistentQueueItem>

    fun deleteAll(): Int
}