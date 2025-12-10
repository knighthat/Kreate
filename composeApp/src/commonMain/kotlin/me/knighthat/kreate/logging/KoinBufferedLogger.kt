package me.knighthat.kreate.logging

import androidx.compose.ui.util.fastForEach
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.koin.core.logger.Level
import org.koin.core.logger.MESSAGE
import org.koin.core.logger.Logger as KoinLogger


class KoinBufferedLogger : KoinLogger(), BufferedLogger {

    companion object {
        private const val LOGGING_TAG = "koin"
    }

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()

    private lateinit var logger: Logger

    override fun flushTo( logger: Logger ) {
        // Redirect all logs to Kermit's logger
        this.logger = logger

        // Dump all buffered logs to provided [logger]
        buffer.fastForEach { (severity, message) ->
            logger.log( severity, LOGGING_TAG, null, message )
        }
    }

    override fun display( level: Level, msg: MESSAGE ) {
        val severity = when( level ) {
            Level.DEBUG     -> Severity.Debug
            Level.INFO      -> Severity.Info
            Level.WARNING   -> Severity.Warn
            Level.ERROR     -> Severity.Error
            Level.NONE      -> Severity.Verbose
        }

        if( ::logger.isInitialized )
            // Kermit's logger is designed to be thread-safe
            // so no "lock" needed for this
            logger.log( severity, LOGGING_TAG, null, msg )
        else
            synchronized( lock ) {
                val entry = LogEntry(severity, msg)
                buffer.addLast( entry )
            }
    }

    private data class LogEntry(
        val severity: Severity,
        val message: String
    )
}