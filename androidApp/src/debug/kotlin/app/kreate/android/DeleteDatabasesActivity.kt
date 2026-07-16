package app.kreate.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.kreate.database.Database
import app.kreate.di.DATABASE_FILENAME
import java.io.File
import kotlin.system.exitProcess


class DeleteDatabasesActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Database.close()
        applicationContext.deleteDatabase( DATABASE_FILENAME )

        val dbLck = File(
            applicationContext.getDatabasePath( DATABASE_FILENAME ).path + ".lck"
        )
        if( dbLck.exists() )
            dbLck.delete()

        finishAffinity()
        exitProcess( 0 )
    }
}