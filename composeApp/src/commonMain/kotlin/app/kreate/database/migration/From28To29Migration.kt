package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL


/**
 * This migration aims to remove explicit prefix from Song's name.
 */
class From28To29Migration : Migration(28, 29) {

    override fun migrate( connection: SQLiteConnection ) {
        connection.execSQL( "ALTER TABLE Song ADD COLUMN is_explicit INTEGER NOT NULL DEFAULT 0;" )
        connection.execSQL("""
            UPDATE Song
            SET
            	is_explicit = 1,
            	title = SUBSTR(title, 3)
            WHERE title LIKE 'e:%';
        """.trimIndent())
    }
}