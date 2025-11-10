package me.knighthat.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import app.kreate.database.models.PersistentQueue
import it.fast4x.rimusic.models.QueuedMediaItem

/**
 * This version introduces [PersistentQueue] table and deletes [QueuedMediaItem].
 *
 *
 */
class From29To30: Migration(29, 30) {

    override fun migrate( connection: SQLiteConnection ) {
        // Dropping [QueuedMediaItem}
        connection.execSQL( "DROP TABLE IF EXISTS QueuedMediaItem" )

        // Creating new table
        connection.execSQL("""
            CREATE TABLE persistent_queue (
                song_id TEXT NOT NULL,
                position INTEGER DEFAULT NULL,
                PRIMARY KEY (song_id),
                FOREIGN KEY (song_id) REFERENCES Song(id) 
                    ON DELETE CASCADE 
                    ON UPDATE CASCADE 
            );
        """.trimIndent())

        // Introduce [QueueView]
        connection.execSQL(
            "CREATE VIEW `queue_view` AS SELECT DISTINCT s.*, position FROM persistent_queue JOIN Song s ON s.id = song_id"
        )
    }
}