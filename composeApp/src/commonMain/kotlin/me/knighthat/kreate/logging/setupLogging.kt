package me.knighthat.kreate.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.io.RollingFileLogWriter
import co.touchlab.kermit.io.RollingFileLogWriterConfig
import co.touchlab.kermit.platformLogWriter
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.knighthat.kreate.preference.Preferences
import me.knighthat.kreate.util.getRuntimeLogDir
import me.knighthat.kreate.util.isDebug


fun setupLogging() {
    val dir = Path( getRuntimeLogDir().absolutePath )
    val maxSize = Preferences.RUNTIME_LOG_FILE_SIZE.value
    val numFiles = Preferences.RUNTIME_LOG_NUM_FILES.value
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
        if(isDebug ) Severity.Verbose else severity
    )
}