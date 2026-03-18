package app.kreate.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.kreate.database.AppDatabase
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
import app.kreate.database.migration.From30To31Migration
import app.kreate.database.migration.From34To35Migration
import app.kreate.database.migration.From35To36Migration
import app.kreate.database.migration.From8To9Migration
import kotlinx.coroutines.Dispatchers
import org.koin.core.scope.Scope
import org.koin.dsl.module


expect fun getDatabaseBuilder( scope: Scope ): RoomDatabase.Builder<AppDatabase>

val databaseModule = module {
    single {
        getDatabaseBuilder( this@single )
            .setDriver( BundledSQLiteDriver() )
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
                From30To31Migration(),
                From34To35Migration(),
                From35To36Migration()
            )
            .build()
    }
}