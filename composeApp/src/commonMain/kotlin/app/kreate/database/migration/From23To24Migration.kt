package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From23To24Migration : Migration(23, 24) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE SongPlaylistMap ADD COLUMN setVideoId TEXT;")
    }
}