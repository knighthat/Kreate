package me.knighthat.kreate.util

import java.io.File
import java.util.Date


expect class CrashHandler: AbstractCrashHandler

abstract class AbstractCrashHandler: Thread.UncaughtExceptionHandler {

    abstract fun writeReport( file: File, e: Throwable )

    override fun uncaughtException( t: Thread, e: Throwable ) {
        // Make sure error still prints to [System.err]
        if( isDebug ) e.printStackTrace()

        val dir = getCrashLogDir()
        val datetime = TimeDateUtils.logFileName().format( Date() )
        val logFile = dir.resolve( "Kreate_crashlog_$datetime.log" )

        if( !logFile.exists() )
            logFile.createNewFile()

        writeReport( logFile, e )
    }
}