package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From14To15Migration : Migration(14, 15) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            INSERT INTO Format (songId, loudnessDb, contentLength)
            SELECT id, loudnessDb, contentLength 
            FROM Song;
        """.trimIndent())

        connection.execSQL("CREATE TABLE IF NOT EXISTS `Song_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artistsText` TEXT, `durationText` TEXT NOT NULL, `thumbnailUrl` TEXT, `lyrics` TEXT, `likedAt` INTEGER, `totalPlayTimeMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")

        connection.execSQL("INSERT INTO Song_new(id, title, artistsText, durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs) SELECT id, title, artistsText, durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs FROM Song;")
        connection.execSQL("DROP TABLE Song;")
        connection.execSQL("ALTER TABLE Song_new RENAME TO Song;")
    }
}