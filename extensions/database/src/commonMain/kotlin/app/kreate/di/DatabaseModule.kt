package app.kreate.di

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.kreate.database.AppDatabase
import app.kreate.internal.database.migrations.From10To11Migration
import app.kreate.internal.database.migrations.From14To15Migration
import app.kreate.internal.database.migrations.From22To23Migration
import app.kreate.internal.database.migrations.From23To24Migration
import app.kreate.internal.database.migrations.From24To25Migration
import app.kreate.internal.database.migrations.From25To26Migration
import app.kreate.internal.database.migrations.From26To27Migration
import app.kreate.internal.database.migrations.From27To28Migration
import app.kreate.internal.database.migrations.From28To29Migration
import app.kreate.internal.database.migrations.From29To30Migration
import app.kreate.internal.database.migrations.From30To31Migration
import app.kreate.internal.database.migrations.From34To35Migration
import app.kreate.internal.database.migrations.From35To36Migration
import app.kreate.internal.database.migrations.From8To9Migration
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.scope.Scope
import org.koin.dsl.module


expect fun getDatabaseBuilder( scope: Scope ): RoomDatabase.Builder<AppDatabase>

val databaseModule = module {
    single {
        getDatabaseBuilder( this@single )
            .setDriver( BundledSQLiteDriver() )
            .setQueryCoroutineContext( Dispatchers.IO )
            .addCallback( DatabaseInitCallback() )
            .addMigrations(
                From8To9Migration(),
                From10To11Migration(),
                From14To15Migration(),
                From22To23Migration(),
                From23To24Migration(),
                From24To25Migration(),
                From25To26Migration(),
                From26To27Migration(),
                From27To28Migration(),
                From28To29Migration(),
                From29To30Migration(),
                From30To31Migration(),
                From34To35Migration(),
                From35To36Migration()
            )
            .build()
    }
}

private class DatabaseInitCallback : RoomDatabase.Callback() {

    override fun onOpen( connection: SQLiteConnection ) {
        super.onOpen( connection )

        // Clear ghost maps on open
        try {
            connection.execSQL("""
                DELETE FROM formats 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
                connection.execSQL("""
                DELETE FROM lyrics 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
                connection.execSQL("""
                DELETE FROM persistent_queue 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
                connection.execSQL("""
                DELETE FROM playback_history 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
                connection.execSQL("""
                DELETE FROM song_album_map 
                WHERE song_id NOT IN (SELECT id FROM songs)
                OR album_id NOT IN (SELECT id FROM albums);
            """.trimIndent())
                connection.execSQL("""
                DELETE FROM song_artist_map 
                WHERE song_id NOT IN (SELECT id FROM songs)
                OR artist_id NOT IN (SELECT id FROM artists);
            """.trimIndent())
                connection.execSQL("""
                DELETE FROM song_playlist_map 
                WHERE song_id NOT IN (SELECT id FROM songs)
                OR playlist_id NOT IN (SELECT id FROM playlists);
            """.trimIndent())
        } catch( err: Exception ) {
            Logger.e( err, "DatabaseInitCallback" ) { "failed to clear ghost maps" }
        }


        // Enables foreign key constraint enforcement.
        // Enforces @ForeignKey annotations, operations such as
        // onUpdate and onDelete will be enacted with this enabled.
        connection.execSQL( "PRAGMA foreign_keys = ON;" )
        // WAL allows simultaneous reads and writes.
        // It significantly boosts performance for concurrent database operations.
        // SQLite driver is included with build so this feature is safe to use.
        connection.execSQL( "PRAGMA journal_mode = WAL;" )
        // NORMAL is much faster and completely safe when combined with WAL mode,
        // as it still guarantees database integrity in the event of an application crash.
        connection.execSQL( "PRAGMA synchronous = NORMAL;" )
        // Sets a timeout (in milliseconds) for how long SQLite will wait for
        // a locked table to clear before throwing a [SQLiteDatabaseLockedException].
        connection.execSQL( "PRAGMA busy_timeout = 3000;" )
    }
}