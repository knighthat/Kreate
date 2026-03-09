package app.kreate.logging


import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.util.getRuntimeLogDir
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.io.RollingFileLogWriter
import co.touchlab.kermit.io.RollingFileLogWriterConfig
import co.touchlab.kermit.platformLogWriter
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem


fun setupLogging( vararg bufferedLoggers: BufferedLogger ) {
    val dir = Path( getRuntimeLogDir().absolutePath )
    val maxSize = Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE.value
    val numFiles = Preferences.RUNTIME_LOG_FILE_COUNT.value
    val severity = Preferences.RUNTIME_LOG_SEVERITY.value

    val config = RollingFileLogWriterConfig(
        logFileName = "logs",
        logFilePath = dir,
        rollOnSize = maxSize,
        maxLogFiles = numFiles
    )
    val fileWriter = RollingFileLogWriter(
        config = config,
        fileSystem = SystemFileSystem
    )

    Logger.setLogWriters( platformLogWriter(), fileWriter )
    Logger.setMinSeverity(
        // Override severity when in debug mode
        if( BuildConfig.DEBUG ) Severity.Verbose else severity
    )

    if( Logger.config.minSeverity == Severity.Verbose )
        Logger.v( tag = "System" ) { "Verbose mode enabled!" }

    // Dump logs from BufferedLogger to current logger
    bufferedLoggers.forEach { it.flushTo( Logger ) }
}