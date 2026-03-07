package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From26To27Migration : Migration(26, 27) {

    override fun migrate(connection: SQLiteConnection) {
        try {
            connection.execSQL("ALTER TABLE Album ADD COLUMN isYoutubeAlbum INTEGER NOT NULL DEFAULT 0;")
        } catch (e: Exception) {
            println("Database From26To27Migration error ${e.stackTraceToString()}")
        }
        try {
            connection.execSQL("ALTER TABLE Artist ADD COLUMN isYoutubeArtist INTEGER NOT NULL DEFAULT 0;")
        } catch (e: Exception) {
            println("Database From26To27Migration error ${e.stackTraceToString()}")
        }
        try {
            connection.execSQL("ALTER TABLE SongPlaylistMap ADD COLUMN dateAdded INTEGER NULL;")
        } catch (e: Exception) {
            println("Database From26To27Migration error ${e.stackTraceToString()}")
        }

    }
}