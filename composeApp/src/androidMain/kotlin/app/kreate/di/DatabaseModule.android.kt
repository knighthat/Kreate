package app.kreate.di

import androidx.room.Room
import androidx.room.RoomDatabase
import app.kreate.android.Preferences
import app.kreate.database.AppDatabase
import org.koin.core.scope.Scope

actual fun getDatabaseBuilder( scope: Scope ): RoomDatabase.Builder<AppDatabase> {
    val filename = if ( Preferences.ACTIVE_PROFILE.value == "default" )
        AppDatabase.FILENAME
    else
        "data_${Preferences.ACTIVE_PROFILE.value}.db"

    return Room.databaseBuilder(scope.get(), filename )
}