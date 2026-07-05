package app.kreate.di

import androidx.room.RoomDatabase
import app.kreate.database.AppDatabase
import org.koin.core.scope.Scope


actual fun getDatabaseBuilder(scope: Scope): RoomDatabase.Builder<AppDatabase> {
    TODO("Not yet implemented")
}