package app.kreate.android.utils

import me.knighthat.logging.Logger
import co.touchlab.kermit.Logger as KermitLogger

class DiscordLogger: Logger.Handler {

    companion object {

        private const val LOGGING_TAG = "discord"
    }

    override fun verbose( tag: String, verbose: String ) = KermitLogger.v( verbose, tag = LOGGING_TAG )

    override fun debug( tag: String, debug: String ) = KermitLogger.d( debug, tag = LOGGING_TAG )

    override fun info( tag: String, info: String ) = KermitLogger.i( info, tag = LOGGING_TAG )

    override fun warning( tag: String, warning: String ) = KermitLogger.w( warning, tag = LOGGING_TAG )

    override fun error( tag: String, error: Throwable, message: String? ) =
        KermitLogger.e( error, tag = LOGGING_TAG ) { message.orEmpty() }
}