package app.kreate.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.kreate.database.migration.From10To11Migration
import app.kreate.database.migration.From14To15Migration
import app.kreate.database.migration.From22To23Migration
import app.kreate.database.migration.From23To24Migration
import app.kreate.database.migration.From24To25Migration
import app.kreate.database.migration.From25To26Migration
import app.kreate.database.migration.From26To27Migration
import app.kreate.database.migration.From27To28Migration
import app.kreate.database.migration.From28To29Migration
import app.kreate.database.migration.From29To30Migration
import app.kreate.database.migration.From34To35Migration
import app.kreate.database.migration.From35To36Migration
import app.kreate.database.migration.From8To9Migration
import kotlinx.coroutines.Dispatchers


fun getAppDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
) =
    builder.setDriver( BundledSQLiteDriver() )
           .setQueryCoroutineContext( Dispatchers.IO )
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
               From34To35Migration(),
               From35To36Migration()
           )
           .build()