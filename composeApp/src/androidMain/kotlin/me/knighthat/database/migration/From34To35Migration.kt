package me.knighthat.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL


/**
 * This migration aims to replace current persistent_queue layout.
 * This process includes removing `id`, and `song` (which is blob
 * stores [androidx.media3.common.MediaItem] instance).
 *
 * Song is now `song_id` which is foreign key linked to [app.kreate.database.models.Song.id].
 *
 * To simplify the process, old table is dropped and new table
 * gets created from scratch.
 */
class From34To35Migration : Migration(34, 35) {

    override fun migrate( connection: SQLiteConnection ) {
        connection.execSQL( "DROP TABLE persistent_queue" )
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS persistent_queue (
                `song_id` TEXT NOT NULL, 
                `position` INTEGER, PRIMARY KEY(`song_id`), 
                FOREIGN KEY(`song_id`) 
                    REFERENCES `songs`(`id`) 
                    ON UPDATE CASCADE 
                    ON DELETE CASCADE 
            )
        """.trimIndent())
    }
}