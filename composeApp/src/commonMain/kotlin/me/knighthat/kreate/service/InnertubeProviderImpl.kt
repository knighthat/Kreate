package me.knighthat.kreate.service

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.toMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.knighthat.innertube.Constants
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.request.Request
import me.knighthat.innertube.request.body.Context
import me.knighthat.innertube.response.Response
import org.jetbrains.annotations.Blocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class InnertubeProviderImpl: Innertube.Provider, KoinComponent {

    override val cookies: String = ""
    override val dataSyncId: String? = null
    override val visitorData: String = Constants.CHROME_WINDOWS_VISITOR_DATA

    @Blocking
    override fun execute( request: Request ): Response = runBlocking( Dispatchers.IO ) {
        val networkService by inject<HttpClient>()

        val result = networkService.request( request.url ) {
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
            }
        }

        Response(
            result.status.value, "", result.headers.toMap(), result.bodyAsText()
        )
    }
}