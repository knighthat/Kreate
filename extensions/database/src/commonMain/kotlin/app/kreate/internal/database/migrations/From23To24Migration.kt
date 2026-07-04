package app.kreate.internal.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL


internal class From23To24Migration : Migration(23, 24) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE SongPlaylistMap ADD COLUMN setVideoId TEXT;")
    }
}