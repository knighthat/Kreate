package app.kreate.android.service.innertube

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import app.kreate.android.Preferences
import app.kreate.android.service.NetworkService
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.sha1
import io.ktor.util.toMap
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.knighthat.innertube.Constants
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.request.Request
import me.knighthat.innertube.request.body.Context
import me.knighthat.innertube.response.Response
import org.jetbrains.annotations.Blocking
import timber.log.Timber


class InnertubeProvider: Innertube.Provider {

    companion object {
        val COOKIE_MAP by derivedStateOf {
            if( !isAtLeastAndroid6 || Preferences.YOUTUBE_COOKIES.value.isBlank() )
                return@derivedStateOf emptyMap()

            runCatching {
                Preferences.YOUTUBE_COOKIES
                           .value
                           .split( ';' )
                           .associate {
                               val (k, v) = it.split('=', limit = 2)
                               k.trim() to v.trim()
                           }
            }.onFailure {
                it.printStackTrace()
                Timber.tag( "InnertubeProvider" ).e( "Cookie parser failed!" )
            }.getOrElse { emptyMap() }
        }
    }

    override val cookies: String
        get() = if( isAtLeastAndroid6 ) Preferences.YOUTUBE_COOKIES.value else ""
    override val dataSyncId: String
        get() = if( isAtLeastAndroid6 ) Preferences.YOUTUBE_SYNC_ID.value else ""
    override val visitorData: String
        get() = if( isAtLeastAndroid6 ) Preferences.YOUTUBE_VISITOR_DATA.value else ""

    @Blocking
    override fun execute( request: Request ): Response = runBlocking( Dispatchers.IO ) {
        val result = NetworkService.client.request( request.url ) {
            accept( ContentType.Application.Json )
            contentType( ContentType.Application.Json )
            method = HttpMethod.parse( request.httpMethod )

            // Disable pretty print - potentially save data
            url {
                parameters.append( "prettyPrint", "false" )
            }
            // Only setBody when it's not null
            request.dataToSend?.also( this::setBody )
            // Add headers
            request.headers
                   .forEach( headers::appendAll )

            headers {
                append( "X-Goog-Api-Format-Version", "1" )

                val client = request.dataToSend?.context?.client

                val ytUrl = "${url.protocol.name}://${url.host}"
                append( "X-Origin", client?.originalUrl ?: ytUrl )
                append( "Referer", client?.referer ?: ytUrl )

                val nonnullClient = client ?: Context.WEB_REMIX_DEFAULT.client
                append( "X-YouTube-Client-Name", nonnullClient.xClientName.toString() )
                append( "X-YouTube-Client-Version", nonnullClient.clientVersion )

                // Series of checks, if 1 fails, then don't send login information
                if (
                    !request.useLogin
                    || cookies.isBlank()
                    || "SAPISID" !in COOKIE_MAP
                ) return@headers

                append( "cookie", cookies )

                val currentTime = System.currentTimeMillis() / 1000
                val sapisidHash: String
                "%d %s %s".format( currentTime, COOKIE_MAP["SAPISID"], Constants.YOUTUBE_MUSIC_URL )
                          .toByteArray()
                          .let( ::sha1 )
                          .joinToString("") { "%02x".format(it) }
                          .also { sapisidHash = it }
                append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash} SAPISID1PHASH ${currentTime}_${sapisidHash} SAPISID3PHASH ${currentTime}_${sapisidHash}")
            }
        }

        Response(
            result.status.value, "", result.headers.toMap(), result.bodyAsText()
        )
    }
}