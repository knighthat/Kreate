package me.knighthat.kreate.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import me.knighthat.kreate.util.isDebug
import org.koin.dsl.module


private val KTOR_LOGGER_TO_KERMIT = object : Logger {

    override fun log( message: String ) =
        // Automatically logs everything at verbose level
        // This will prevent logging unnecessary things in prod
        // but still be useful in dev.
        co.touchlab.kermit.Logger.v( "Ktor" ) { message }
}

expect val networkEngine: HttpClientEngineFactory<HttpClientEngineConfig>

@OptIn(ExperimentalSerializationApi::class)
val networkModule = module {
    single {
        HttpClient(networkEngine) {
            expectSuccess = true

            install( ContentNegotiation ) {
                json(Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    classDiscriminatorMode = ClassDiscriminatorMode.NONE
                })
            }
            install( ContentEncoding ) {
                gzip( 1f )
                deflate( .9f )
            }
            install(Logging ) {
                logger = KTOR_LOGGER_TO_KERMIT
                level = if( isDebug ) LogLevel.ALL else LogLevel.BODY
            }
        }
    }
}