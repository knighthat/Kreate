package app.kreate.internal.database.callbacks

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger


internal class EnableFeatures : RoomDatabase.Callback() {

    override fun onOpen( connection: SQLiteConnection ) =
        try {
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
        } catch ( err: Exception ) {
            Logger.e( "Failed to enable features", err, "EnableFeatures" )
        }
}