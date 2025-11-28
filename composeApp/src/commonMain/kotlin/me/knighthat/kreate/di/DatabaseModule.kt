package me.knighthat.kreate.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import me.knighthat.kreate.database.UserDatabase
import org.koin.core.module.Module
import org.koin.dsl.module


expect val platformDatabaseModule: Module

val databaseModule = module {
    single {
        val builder: RoomDatabase.Builder<UserDatabase> = get()
        builder.setDriver( BundledSQLiteDriver() )
               .setQueryCoroutineContext( Dispatchers.IO )
               .build()
    }
}