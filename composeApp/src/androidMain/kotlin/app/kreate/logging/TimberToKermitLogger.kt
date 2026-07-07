package app.kreate.logging

import android.util.Log
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import timber.log.Timber


class TimberToKermitLogger : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val severity = when( priority ) {
            Log.VERBOSE -> Severity.Verbose
            Log.DEBUG   -> Severity.Debug
            Log.INFO    -> Severity.Info
            Log.WARN    -> Severity.Warn
            Log.ERROR   -> Severity.Error
            Log.ASSERT  -> Severity.Assert
            else        -> Severity.Debug
        }
        Logger.log( severity, tag ?: "Timber", t, message )
    }
}