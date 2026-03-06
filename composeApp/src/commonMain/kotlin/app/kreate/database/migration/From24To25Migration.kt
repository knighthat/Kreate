package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From24To25Migration : Migration(24, 25) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Playlist ADD COLUMN isEditable INTEGER NOT NULL DEFAULT 0;")
    }
}