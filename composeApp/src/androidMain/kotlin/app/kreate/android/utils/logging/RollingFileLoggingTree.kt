package app.kreate.android.utils.logging

import android.util.Log
import androidx.compose.ui.util.fastForEach
import app.kreate.android.Preferences
import me.knighthat.utils.TimeDateUtils
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date

class RollingFileLoggingTree(
    cacheDir: File,
    private val fileCount: Int,
    private val maxSizePerFile: Long
): Timber.DebugTree(), Closeable {

    companion object {
        val levelStringMapping = mapOf(
            Log.VERBOSE to "VERBOSE",
            Log.DEBUG   to "DEBUG",
            Log.INFO    to "INFO",
            Log.WARN    to "WARN",
            Log.ERROR   to "ERROR",
            Log.ASSERT  to "ASSERT"
        )
        private val timestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern( "HH:mm:ss" )
    }

    private val logDir: File = cacheDir.resolve( "logs" )

    @Volatile
    private var logFile: File
    @Volatile
    private var writer: PrintWriter

    init {
        require( fileCount > 0 ) {
            "${Preferences.RUNTIME_LOG_FILE_COUNT.key} must be a positive number! ${Preferences.RUNTIME_LOG_FILE_COUNT.value}"
        }
        require( maxSizePerFile > 0L ) {
            "${Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE.key} must be a positive number! ${Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE.value}"
        }

        if( !logDir.exists() ) logDir.mkdirs()

        this.logFile = createLogFile()
        this.writer = this.logFile.printWriter()
    }

    private fun createLogFile(): File {
        val fileNameFormat = TimeDateUtils.logFileName()

        // Creating log file before the purge to make
        // the counting more reliable
        val fileName = fileNameFormat.format( Date() ).plus( ".log" )
        val logFile = logDir.resolve( fileName ).apply {
            if( !exists() ) createNewFile()
            if( !canWrite() ) setWritable( true, true )
        }

        //<editor-fold desc="Remove old log files">
        val prevLogFiles = logDir.listFiles {
            try {
                fileNameFormat.parse( it.nameWithoutExtension )
                true
            } catch ( _: Exception ) {
                false
            }
        }.orEmpty()

        if( prevLogFiles.size >= this.fileCount )
            prevLogFiles.sortedBy { fileNameFormat.parse( it.nameWithoutExtension ) }
                        .take( prevLogFiles.size - this.fileCount )
                        .fastForEach( File::delete )
        //</editor-fold>

        return logFile
    }

    private fun canRollOver(): Boolean = this.logFile.length() >= maxSizePerFile

    override fun close() = this.writer.close()

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        if( priority < Preferences.RUNTIME_LOG_LEVEL.value) return

        this.writer.format(
            // [15:03:00/DEBUG] Main: Log message goes here
            "[%s/%s] %s: %s\n",
            /* timeStamp */ timestampFormat.format(LocalTime.now() ),
            /* levelName */ levelStringMapping[priority],
            /* tagName */ tag ?: Thread.currentThread().name,
            /* message */ message
        )
        t?.printStackTrace( this.writer )
        this.writer.flush()

        if( canRollOver() ) {
            close()

            this.logFile = createLogFile()
            this.writer = this.logFile.printWriter()
        }
    }
}