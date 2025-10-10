package me.knighthat.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL


/**
 * This version introduces `is_explicit`, `is_local` columns to Song table.
 *
 * All prefixes are converted accordingly.
 */
class From28to29: Migration(28,29) {

    override fun migrate(connection: SQLiteConnection ) {
        // Adding `is_explicit` col
        connection.execSQL("""
            ALTER TABLE Song ADD COLUMN is_explicit INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        // Set is_explicit to `true` if name contains 'e:'
        connection.execSQL("""
            UPDATE Song 
            SET is_explicit = 1,
                title = REPLACE(title, 'e:', '')
            WHERE title LIKE 'e:%'
        """.trimIndent())

        // Adding `is_local` col
        connection.execSQL("""
            ALTER TABLE Song ADD COLUMN is_local INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        // Set is_local to `true` if name contains 'local:'
        connection.execSQL("""
            UPDATE Song 
            SET is_local = 1,
                id = REPLACE(id, 'local:', '')
            WHERE title LIKE 'local:%'
        """.trimIndent())
    }
}