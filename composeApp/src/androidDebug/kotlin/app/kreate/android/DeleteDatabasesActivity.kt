package app.kreate.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import it.fast4x.rimusic.Database
import java.io.File
import kotlin.system.exitProcess


class DeleteDatabasesActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Database.close()
        applicationContext.deleteDatabase( Database.FILE_NAME )

        val dbLck = File(
            applicationContext.getDatabasePath( Database.FILE_NAME ).path + ".lck"
        )
        if( dbLck.exists() )
            dbLck.delete()

        finishAffinity()
        exitProcess( 0 )
    }
}