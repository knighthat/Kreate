package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL


/**
 * This migration aims to remove `local:` prefix from song's id.
 * This process also adds new column `is_local` (which is a boolean),
 * and turns value to `true` during the migration if id starts with `local:`
 */
class From35To36Migration : Migration(35, 36) {

    override fun migrate( connection: SQLiteConnection ) {
        connection.execSQL( "ALTER TABLE songs ADD COLUMN is_local INTEGER NOT NULL DEFAULT 0;" )
        connection.execSQL("""
            UPDATE songs
            SET
            	is_local = 1,
            	id = SUBSTR(id, 7)
            WHERE id LIKE 'local:%';
        """.trimIndent())
    }
}