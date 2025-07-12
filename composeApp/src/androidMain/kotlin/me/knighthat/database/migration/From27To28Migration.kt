package me.knighthat.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber
import java.sql.SQLException

class From27To28Migration: Migration(27, 28) {

    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("""
                DELETE FROM Playlist WHERE name LIKE 'piped:%' COLLATE NOCASE
            """.trimIndent())
        } catch ( e: SQLException ) {
            Timber.tag( this::class.simpleName.orEmpty() )
                  .e( "failed to  delete Piped playlists" )
        }
    }
}