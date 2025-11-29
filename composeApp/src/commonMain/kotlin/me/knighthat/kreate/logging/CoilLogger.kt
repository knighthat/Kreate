package me.knighthat.kreate.logging

import co.touchlab.kermit.Severity
import coil3.util.Logger
import me.knighthat.kreate.preference.Preferences
import me.knighthat.kreate.util.isDebug
import co.touchlab.kermit.Logger as Kermit


class CoilLogger : Logger {

    /**
     * The minimum level for this logger to log.
     *
     * Defaults to [Logger.Level.Verbose] if [isDebug] is `true`.
     * Otherwise, use whatever current value of [Preferences.RUNTIME_LOG_SEVERITY]
     *
     * **ATTENTION**: Setter has no effect
     */
    override var minLevel: Logger.Level
        get() =
            if ( isDebug )
                Logger.Level.Verbose
            else
                when ( Preferences.RUNTIME_LOG_SEVERITY.value ) {
                    Severity.Verbose -> Logger.Level.Verbose
                    Severity.Debug -> Logger.Level.Debug
                    Severity.Info -> Logger.Level.Info
                    Severity.Warn -> Logger.Level.Warn
                    else -> Logger.Level.Error
                }
        set(value) {}

    override fun log( tag: String, level: Logger.Level, message: String?, throwable: Throwable? ) {
        // Don't wast resource logging empty message and null throwable
        if( message.isNullOrBlank() && throwable == null ) return

        val severity = when( level ) {
            Logger.Level.Verbose -> Severity.Verbose
            Logger.Level.Debug -> Severity.Debug
            Logger.Level.Info -> Severity.Info
            Logger.Level.Warn -> Severity.Warn
            Logger.Level.Error -> Severity.Error
        }

        Kermit.log( severity, tag, throwable, message.orEmpty() )
    }
}