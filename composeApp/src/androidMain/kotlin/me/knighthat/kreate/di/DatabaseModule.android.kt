package me.knighthat.kreate.di

import android.content.Context
import androidx.room.Room
import me.knighthat.kreate.database.UserDatabase
import org.koin.dsl.module


actual val platformDatabaseModule = module {
    single {
        val context: Context = get()
        Room.databaseBuilder<UserDatabase>( context.applicationContext, UserDatabase.FILENAME )
    }
}