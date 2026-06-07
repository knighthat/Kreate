package app.kreate.logging

import app.kreate.android.BuildConfig
import co.touchlab.kermit.Severity
import coil3.request.NullRequestDataException
import coil3.util.Logger
import okio.FileNotFoundException
import co.touchlab.kermit.Logger as Kermit


class CoilLogger : Logger {

    /**
     * The minimum level for this logger to log.
     *
     * Defaults to [Logger.Level.Verbose] if [BuildConfig.DEBUG] is `true`.
     * Otherwise, use whatever current value of [Preferences.RUNTIME_LOG_SEVERITY]
     *
     * **ATTENTION**: Setter has no effect
     */
    override var minLevel: Logger.Level
        get() =
            if ( BuildConfig.DEBUG )
                Logger.Level.Verbose
            else
                when ( app.kreate.preferences.Preferences.RUNTIME_LOG_SEVERITY.value ) {
                    Severity.Verbose -> Logger.Level.Verbose
                    Severity.Debug -> Logger.Level.Debug
                    Severity.Info -> Logger.Level.Info
                    Severity.Warn -> Logger.Level.Warn
                    else -> Logger.Level.Error
                }
        set(_) { /* Not supported */ }

    override fun log( tag: String, level: Logger.Level, message: String?, throwable: Throwable? ) {
        // Don't wast resource logging empty message and null throwable
        if( message.isNullOrBlank() && throwable == null ) return
        // Ignore successful retrieval messages (unless in debug or lower)
        val containsSuccessful = message?.contains("Successful", true) ?: false
        if( level === Logger.Level.Info && containsSuccessful && !BuildConfig.DEBUG ) return
        // Message and stacktrace from these exception is usually useless
        if( throwable is NullRequestDataException || throwable is IllegalStateException ) return

        val severity = when( level ) {
            Logger.Level.Verbose -> Severity.Verbose
            Logger.Level.Debug -> Severity.Debug
            Logger.Level.Info -> Severity.Info
            Logger.Level.Warn -> Severity.Warn
            Logger.Level.Error -> Severity.Error
        }
        val message = when( throwable ) {
            // If file isn't found, only print error message
            is FileNotFoundException    -> throwable.message
            else                        -> message
        }
        val throwable = when( throwable ) {
            // Don't print stack trace for this exception
            is FileNotFoundException    -> null
            else                        -> throwable
        }
        Kermit.log( severity, tag, throwable, message.orEmpty() )
    }
}