/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.HttpClientFactory
import me.knighthat.kreate3.provider.networkProxy
import me.knighthat.kreate3.provider.proxy
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Networking shared by the whole app. Timeouts and retries come from [Preferences] when the
 * clients are built, so a change applies from the next launch.
 *
 * - [OkHttpClient]: the one client, so connections and threads are pooled. Derive a variant
 *   with `client.newBuilder()`, it shares the pool. It has no HTTP cache on purpose.
 * - [Json]: the one configuration for reading and writing JSON.
 * - [HttpClientFactory]: Ktor on top of an [OkHttpClient], with JSON bodies, retries on server
 *   errors and a one line log per request. Compression needs no plugin, OkHttp handles gzip.
 *   Sources make theirs with it on the client they derive, like one going through a proxy.
 * - [HttpClient]: the factory's client, for what isn't a source, on a variant of that same
 *   [OkHttpClient] going through the proxy of [Preferences.NETWORK_PROXY_ENABLED]. Sources go
 *   through it too, unless they have a proxy of their own.
 */
val networkModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(Preferences.NETWORK_CONNECT_TIMEOUT.value.toLong(), TimeUnit.SECONDS)
            .readTimeout(Preferences.NETWORK_READ_TIMEOUT.value.toLong(), TimeUnit.SECONDS)
            .build()
    }

    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        val json = get<Json>()
        HttpClientFactory { okHttp ->
            HttpClient(OkHttp) {
                engine { preconfigured = okHttp }

                install(ContentNegotiation) { json(json) }

                install(HttpRequestRetry) {
                    retryOnServerErrors(Preferences.NETWORK_MAX_RETRIES.value)
                    exponentialDelay()
                }

                // Url and status only, no headers or bodies, they may hold personal data
                install(Logging) {
                    logger = Logger.ANDROID
                    level = LogLevel.INFO
                }
            }
        }
    }

    single {
        val okHttp = get<OkHttpClient>().newBuilder().proxy("network", ::networkProxy).build()
        get<HttpClientFactory>().create(okHttp)
    }
}
