package app.kreate.android.utils

import android.app.Activity
import android.content.Context
import androidx.core.net.toUri
import app.kreate.android.BuildConfig
import me.knighthat.utils.TimeDateUtils
import java.io.File
import java.io.PrintWriter
import java.util.Date
import kotlin.system.exitProcess

/**
 * Handle writing crash log into system file
 */
class CrashHandler(
    private val context: Context
): Thread.UncaughtExceptionHandler {

    companion object {
        // Kreate_crashlog_2025-8-21_00-00-00.log
        // Also captures the date & time
        val fileNameRegex = Regex("^${BuildConfig.APP_NAME}_crashlog_(\\d{4}-\\d{2}-\\d{2}_[0-2]\\d-[0-5]\\d-[0-5]\\d).log$")

        fun getDir( context: Context ): File =
            context.filesDir.resolve( "crashlogs" )
    }

    /**
     *  Write stacktrace to a file in `files` folder for consistency.
     */
    override fun uncaughtException( t: Thread, e: Throwable ) {
        // Make sure error still prints to [System.err]
        if( BuildConfig.DEBUG ) e.printStackTrace()

        val crashlogsDir = getDir( context )
        if( !crashlogsDir.exists() )
            crashlogsDir.mkdirs()

        val datetime = TimeDateUtils.logFileName().format( Date() )
        val logFile = crashlogsDir.resolve(
            "${BuildConfig.APP_NAME}_crashlog_$datetime.log"
        )
        if( !logFile.exists() )
            logFile.createNewFile()

        context.contentResolver.openOutputStream( logFile.toUri() )?.use { outStream ->
            // Similar to [Throwable.stackTraceToString], but writing to [outStream] instead
            PrintWriter(outStream).also( e::printStackTrace ).flush()
        }

        (context as? Activity)?.finishAndRemoveTask()
        exitProcess( 1 )
    }
}