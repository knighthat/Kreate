package app.kreate.di

import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.enums.DohServer
import co.touchlab.kermit.Logger
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
import me.knighthat.innertube.Constants
import me.knighthat.utils.Toaster
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit


private const val LOGGING_TAG = "Networking"

@ExperimentalSerializationApi
private val JSON: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false

    // Exclude ("type": "me.knighthat.innertube.*")
    // since there's no intention to deserialize json
    // string back to the class
    classDiscriminatorMode = ClassDiscriminatorMode.NONE
}

private fun verifyProxy( proxy: Proxy, url: String = "https://httpbin.org/ip" ): Boolean =
    runCatching {
        OkHttpClient.Builder()
                    .proxy( proxy )
                    .connectTimeout( 3, TimeUnit.SECONDS )
                    .callTimeout( 5, TimeUnit.SECONDS )
                    .build()
                    .newCall(
                        Request.Builder()
                               .head()
                               .url( url )
                               .build()
                    )
                    .execute()
                    .use( Response::isSuccessful )
    }.onFailure { err ->
        Logger.e( err, LOGGING_TAG ) { "Failed to connect to $url via proxy $proxy" }
        Toaster.w( R.string.error_failed_to_verify_proxy )
    }.getOrDefault( false )

private fun verifyDoH( resolver: DnsOverHttps, addresses: List<InetAddress>, domain: String = "google.com" ): Boolean =
    runCatching {
        val results = resolver.lookup( domain )
        Logger.d( tag = LOGGING_TAG ) { "Resolved $domain to ${results.size} addresses" }

        return results.isNotEmpty()
    }.onFailure { err ->
        // Failed to resolve "google.com" with [/1.1.1.1, /1.0.0.1, /2606:4700:4700::1111, /2606:4700:4700::1001]
        Logger.e( err, LOGGING_TAG ) { "Failed to resolve \"$domain\" with $addresses" }
        Toaster.w( R.string.error_failed_to_verify_doh )
    }.getOrDefault( false )

actual val networkModule: Module = module {
    factory<Proxy> {       // Recreate proxy instance every time it's called
        if( !Preferences.IS_PROXY_ENABLED.value ) {
            Logger.d( tag = LOGGING_TAG ) { "Proxy is not enabled" }
            return@factory Proxy.NO_PROXY
        }

        val proxy = Proxy(
            Preferences.PROXY_SCHEME.value,
            InetSocketAddress(Preferences.PROXY_HOST.value, Preferences.PROXY_PORT.value)
        )
        // Must verify to prevent network failure
        runBlocking( Dispatchers.IO ) { proxy.takeIf( ::verifyProxy ) ?: Proxy.NO_PROXY }
    }
    factory<Dns> {
        if( Preferences.DOH_SERVER.value == DohServer.NONE ) {
            Logger.d( tag = LOGGING_TAG ) { "DoH is not enabled. Using system's DNS" }
            return@factory Dns.SYSTEM
        }

        val client = OkHttpClient.Builder().build()
        val url = Preferences.DOH_SERVER.value.url!!        // Cannot be null if other than NONE
        val addresses = Preferences.DOH_SERVER.value.address.map( InetAddress::getByName )

        val dns = DnsOverHttps
            .Builder()
            .client( client )
            .url( url )
            .bootstrapDnsHosts( addresses )
            .build()
        // Must verify to prevent network failure
        runBlocking( Dispatchers.IO ) {
            dns.takeIf { verifyDoH(it, addresses) } ?: Dns.SYSTEM
        }
    }

    single {
        OkHttpClient.Builder()
                    .proxy( get() )
                    .dns( get() )
                    .apply {
                        if( BuildConfig.DEBUG )
                            addInterceptor(
                                HttpLoggingInterceptor().setLevel( HttpLoggingInterceptor.Level.BODY )
                            )
                    }
                    .build()
    }
    single {
        HttpClient(OkHttp) {
            expectSuccess = true

            @OptIn(ExperimentalSerializationApi::class)
            install( ContentNegotiation ) {
                protobuf()
                json( JSON )
            }

            install( ContentEncoding ) {
                gzip( 1f )
                deflate( 0.9F )
            }

            @OptIn(ExperimentalSerializationApi::class)
            install(WebSockets ) {
                contentConverter = KotlinxWebsocketSerializationConverter(JSON)
            }

            engine {
                preconfigured = get()
            }

            defaultRequest {
                url( Constants.YOUTUBE_MUSIC_URL )
                contentType( ContentType.Application.Json )

                url {
                    parameters.append("prettyPrint", "false")
                }
            }
        }
    }
}