package me.knighthat.kreate.di

import androidx.room.Room
import me.knighthat.kreate.database.UserDatabase
import me.knighthat.kreate.util.getDataDir
import org.koin.dsl.module


actual val platformDatabaseModule = module {
    single {
        val dataDir = getDataDir()
        val dbFile = dataDir.resolve( UserDatabase.FILENAME )

        Room.databaseBuilder<UserDatabase>( dbFile.absolutePath)
    }
}