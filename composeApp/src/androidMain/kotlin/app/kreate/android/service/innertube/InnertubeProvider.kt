package app.kreate.android.service.innertube

import app.kreate.android.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.parametersOf
import io.ktor.util.toMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.request.Request
import me.knighthat.innertube.response.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.annotations.Blocking


class InnertubeProvider: Innertube.Provider {

    @OptIn(ExperimentalSerializationApi::class)
    private val JSON: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false

        // Exclude ("type": "me.knighthat.innertube.*")
        // since there's no intention to deserialize json
        // string back to the class
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val CLIENT = HttpClient(OkHttp) {
        expectSuccess = true

        // TODO: Add json (de)serialization once expanded to global use
        install( ContentNegotiation )

        install( ContentEncoding ) {
            gzip(1f)
            deflate(0.9F)
        }

        if( BuildConfig.DEBUG )
            engine {
                addInterceptor(
                    HttpLoggingInterceptor().setLevel( HttpLoggingInterceptor.Level.HEADERS )
                )
                addInterceptor(
                    HttpLoggingInterceptor().setLevel( HttpLoggingInterceptor.Level.BODY )
                )
            }

        defaultRequest {
            accept( ContentType.Application.Json )
            contentType( ContentType.Application.Json )
            parametersOf( "prettyPrint", "false" )
        }
    }

    @Blocking
    override fun execute( request: Request ): Response = runBlocking( Dispatchers.IO ) {
        val result = CLIENT.request( request.url ) {
            method = HttpMethod.parse( request.httpMethod )
            // Only setBody when it's not null
            JSON.encodeToString( request.dataToSend ).also( this::setBody )
            // Disable pretty print - potentially save data
            url {
                parameters.append( "prettyPrint", "false" )
            }
            // Add headers
            request.headers
                   .forEach( headers::appendAll )
        }

        Response(
            result.status.value, "", result.headers.toMap(), result.bodyAsText()
        )
    }
}