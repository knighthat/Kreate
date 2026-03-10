package app.kreate.logging

import co.touchlab.kermit.Logger
import okhttp3.logging.HttpLoggingInterceptor


/**
 * Redirects log messages to Kermit's logger
 */
class OkHttpLogger : HttpLoggingInterceptor.Logger {

    override fun log( message: String ) = Logger.i( tag = "OkHttp" ) { message }
}