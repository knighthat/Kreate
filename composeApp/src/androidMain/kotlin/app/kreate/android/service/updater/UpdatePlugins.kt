package app.kreate.android.service.updater

import android.content.Context
import java.io.File


/**
 * Consists of actions to perform on startup.
 *
 * This is suitable for major upgrade where changes
 * must or should be made to remain compatible
 */
object UpdatePlugins {

    private fun versionCode111( context: Context ) {
        context.filesDir
               .resolve( "coil" )
               .takeIf(File::exists )
               ?.deleteRecursively()
    }

    private fun versionCode115( filesDir: File ) {
        // Deleting old logs
        filesDir.resolve( "logs" )
                .takeIf( File::exists )
                ?.deleteRecursively()
    }

    fun execute( context: Context ) {
        versionCode111( context )
        versionCode115( context.filesDir )
    }
}