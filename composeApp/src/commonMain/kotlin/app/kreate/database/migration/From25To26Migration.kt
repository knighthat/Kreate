package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From25To26Migration : Migration(25, 26) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Playlist ADD COLUMN isYoutubePlaylist INTEGER NOT NULL DEFAULT 0;")
    }
}