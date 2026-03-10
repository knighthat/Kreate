package app.kreate.logging

import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import co.touchlab.kermit.Severity
import coil3.util.Logger
import co.touchlab.kermit.Logger as Kermit


class CoilLogger : Logger {

    /**
     * The minimum level for this logger to log.
     *
     * Defaults to [coil3.util.Logger.Level.Verbose] if [isDebug] is `true`.
     * Otherwise, use whatever current value of [java.util.prefs.Preferences.RUNTIME_LOG_SEVERITY]
     *
     * **ATTENTION**: Setter has no effect
     */
    override var minLevel: Logger.Level
        get() =
            if ( BuildConfig.DEBUG )
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
        // Ignore successful retrieval messages (unless in debug or lower)
        val containsSuccessful = message?.contains("Successful", true) ?: false
        if( level === Logger.Level.Info && containsSuccessful && !BuildConfig.DEBUG ) return

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