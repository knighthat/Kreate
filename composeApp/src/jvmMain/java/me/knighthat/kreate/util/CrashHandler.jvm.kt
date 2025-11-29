package me.knighthat.kreate.util

import java.io.File
import java.io.PrintWriter


actual class CrashHandler : AbstractCrashHandler() {

    override fun writeReport( file: File, e: Throwable ) {
        file.outputStream().use { outStream ->
            val writer = PrintWriter(outStream)

            val appVersion = CrashHandler::class.java.`package`.implementationVersion.orEmpty()

            writer.println( "OS: $OPERATING_SYSTEM" )
            writer.println( "Version: $appVersion" )
            writer.println()
            // Similar to [Throwable.stackTraceToString], but writing to [outStream] instead
            e.printStackTrace( writer )

            writer.flush()
        }
    }

}