package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From22To23Migration : Migration(22, 23) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS Lyrics (`songId` TEXT NOT NULL, `fixed` TEXT, `synced` TEXT, PRIMARY KEY(`songId`), FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")

        connection.execSQL("""
            INSERT INTO Lyrics (songId, fixed, synced)
            SELECT id, lyrics, synchronizedLyrics
            FROM Song;
        """.trimIndent())

        connection.execSQL("CREATE TABLE IF NOT EXISTS Song_new (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artistsText` TEXT, `durationText` TEXT, `thumbnailUrl` TEXT, `likedAt` INTEGER, `totalPlayTimeMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("INSERT INTO Song_new(id, title, artistsText, durationText, thumbnailUrl, likedAt, totalPlayTimeMs) SELECT id, title, artistsText, durationText, thumbnailUrl, likedAt, totalPlayTimeMs FROM Song;")
        connection.execSQL("DROP TABLE Song;")
        connection.execSQL("ALTER TABLE Song_new RENAME TO Song;")
    }
}