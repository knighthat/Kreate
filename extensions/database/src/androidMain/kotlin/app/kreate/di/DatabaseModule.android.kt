package app.kreate.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import app.kreate.internal.database.AbstractRoomDatabase
import org.koin.core.scope.Scope


actual val DATABASE_FILENAME: String
    get() {
        val profile = getActiveProfile()
        return if ( profile == "default" )
            "data.db"
        else
            "data_$profile.db"
    }

internal actual fun Scope.getDatabaseBuilder(): RoomDatabase.Builder<AbstractRoomDatabase> {
    val context: Context = get()
    return Room.databaseBuilder( context, DATABASE_FILENAME )
}