package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From10To11Migration : Migration(10, 11) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            INSERT INTO SongAlbumMap (songId, albumId)
            SELECT id, albumId 
            FROM Song;
        """.trimIndent())

        connection.execSQL("CREATE TABLE IF NOT EXISTS `Song_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artistsText` TEXT, `durationText` TEXT NOT NULL, `thumbnailUrl` TEXT, `lyrics` TEXT, `likedAt` INTEGER, `totalPlayTimeMs` INTEGER NOT NULL, `loudnessDb` REAL, `contentLength` INTEGER, PRIMARY KEY(`id`))")

        connection.execSQL("INSERT INTO Song_new(id, title, artistsText, durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs, loudnessDb, contentLength) SELECT id, title, artistsText, durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs, loudnessDb, contentLength FROM Song;")
        connection.execSQL("DROP TABLE Song;")
        connection.execSQL("ALTER TABLE Song_new RENAME TO Song;")
    }
}