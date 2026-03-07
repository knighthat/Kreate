package app.kreate.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase


fun getAppDatabaseBuilder( context: Context ): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath( AppDatabase.FILENAME )

    return Room.databaseBuilder( appContext, dbFile.absolutePath )
}
