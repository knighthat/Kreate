package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.table.DatabaseTable
import it.fast4x.rimusic.models.QueuedMediaItem

@Dao
@RewriteQueriesToDropUnusedColumns
interface QueuedMediaItemTable: DatabaseTable<QueuedMediaItem> {

    override val tableName: String
        get() = QueuedMediaItem::class.simpleName!!

    @Query("DELETE FROM QueuedMediaItem")
    fun deleteAll(): Int
}