package app.kreate.android.service

import android.widget.Toast
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import com.metrolist.innertube.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import me.knighthat.utils.Toaster
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object NetworkService {

    @OptIn(ExperimentalSerializationApi::class)
    val JSON: Json = Json {
        encodeDefaults
        ignoreUnknownKeys = true
        explicitNulls = false

        // Exclude ("type": "me.knighthat.innertube.*")
        // since there's no intention to deserialize json
        // string back to the class
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }
    val proxy: Proxy
        get() = Proxy(
            Preferences.PROXY_SCHEME.value,
            InetSocketAddress(Preferences.PROXY_HOST.value, Preferences.PROXY_PORT.value)
        )

    val engine by lazy {
        OkHttpClient.Builder()
            .apply {
                if( Preferences.IS_PROXY_ENABLED.value )
                    runBlocking( Dispatchers.IO ) {
                        proxy.takeIf( ::verifyProxy ) ?: Proxy.NO_PROXY
                    }.also( ::proxy )

                if( BuildConfig.DEBUG )
                    addInterceptor(
                        HttpLoggingInterceptor().setLevel( HttpLoggingInterceptor.Level.BODY )
                    )
            }
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true

            install( ContentNegotiation ) {
                protobuf()
                json( JSON )
            }

            install( ContentEncoding ) {
                gzip( 1f )
                deflate( 0.9F )
            }

            install(WebSockets ) {
                contentConverter = KotlinxWebsocketSerializationConverter(JSON)
            }

            engine {
                preconfigured = engine
            }

            defaultRequest {
                url( YouTubeClient.API_URL_YOUTUBE_MUSIC )
                contentType( ContentType.Application.Json )
            }
        }
    }

    fun verifyProxy( proxy: Proxy ): Boolean =
        runCatching {
            OkHttpClient.Builder()
                        .proxy( proxy )
                        .connectTimeout( 3, TimeUnit.SECONDS )
                        .callTimeout( 5, TimeUnit.SECONDS )
                        .build()
                        .newCall(
                            Request.Builder()
                                   .head()
                                   .url( "https://httpbin.org/ip" )
                                   .build()
                        )
                        .execute()
                        .use( Response::isSuccessful )
        }.onFailure { err ->
            Timber.tag( "NetworkService" ).e( err, "failed to verify proxy" )
            err.message?.also {
                Toaster.e( it, Toast.LENGTH_LONG )
            }
        }.getOrDefault( false )
}