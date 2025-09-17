package app.kreate.android.utils

import me.knighthat.logging.Logger
import timber.log.Timber

class DiscordLogger: Logger.Handler {

    companion object {

        private const val LOGGING_TAG = "discord"
    }

    override fun verbose( tag: String, verbose: String ) = Timber.tag( LOGGING_TAG ).v( verbose )

    override fun debug( tag: String, debug: String ) = Timber.tag( LOGGING_TAG ).d( debug )

    override fun info( tag: String, info: String ) = Timber.tag( LOGGING_TAG ).i( info )

    override fun warning( tag: String, warning: String ) = Timber.tag( LOGGING_TAG ).w( warning )

    override fun error( tag: String, error: Throwable, message: String? ) =
        Timber.tag( LOGGING_TAG ).e( error, message )
}