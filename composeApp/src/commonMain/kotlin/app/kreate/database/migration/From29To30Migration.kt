package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL


/**
 * This migration aims to remove `pinned:` and `monthly:`
 * prefixes from Playlists
 */
class From29To30Migration : Migration(29, 30) {

    override fun migrate( connection: SQLiteConnection ) {
        // Adding column `is_pinned` and set it to `true` if Playlist has prefix
        connection.execSQL( "ALTER TABLE Playlist ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0;" )
        connection.execSQL("""
            UPDATE Playlist
            SET
            	is_pinned = 1,
            	name = SUBSTR(name, 7)
            WHERE name LIKE 'pinned:%';
        """.trimIndent())

        // Adding column `is_monthly` and set it to `true` if Playlist has prefix
        connection.execSQL( "ALTER TABLE Playlist ADD COLUMN is_monthly INTEGER NOT NULL DEFAULT 0;" )
        connection.execSQL("""
            UPDATE Playlist
            SET
            	is_monthly = 1,
            	name = SUBSTR(name, 8)
            WHERE name LIKE 'monthly:%';
        """.trimIndent())
    }
}