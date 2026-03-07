package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From27To28Migration: Migration(27, 28) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            DELETE FROM Playlist WHERE name LIKE 'piped:%' COLLATE NOCASE
        """.trimIndent())
    }
}