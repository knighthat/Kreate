package app.kreate.di

import androidx.room.Room
import androidx.room.RoomDatabase
import app.kreate.internal.database.AbstractRoomDatabase
import app.kreate.util.getConfigDir
import org.koin.core.scope.Scope


actual val FILE_NAME: String = "data.db"

internal actual fun Scope.getDatabaseBuilder(): RoomDatabase.Builder<AbstractRoomDatabase> =
    Room.databaseBuilder(
        getConfigDir().resolve( FILE_NAME ).toFile().absolutePath
    )
