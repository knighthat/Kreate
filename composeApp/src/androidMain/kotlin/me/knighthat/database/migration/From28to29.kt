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

        // Adding `is_pinned` col
        connection.execSQL("""
            ALTER TABLE Playlist ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        // Set is_pinned to `true` if name contains 'pinned:'
        connection.execSQL("""
            UPDATE Playlist 
            SET is_pinned = 1,
                id = REPLACE(id, 'pinned:', '')
            WHERE name LIKE 'pinned:%'
        """.trimIndent())

        // Adding `is_monthly` col
        connection.execSQL("""
            ALTER TABLE Playlist ADD COLUMN is_monthly INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        // Set is_monthly to `true` if name contains 'monthly:'
        connection.execSQL("""
            UPDATE Playlist 
            SET is_monthly = 1,
                id = REPLACE(id, 'monthly:', '')
            WHERE name LIKE 'monthly:%'
        """.trimIndent())
    }
}