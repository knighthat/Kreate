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

package me.knighthat.kreate

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
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L
private const val MAX_RETRIES = 3

/**
 * Networking shared by the whole app.
 *
 * - [OkHttpClient]: the one client, so connections and threads are pooled. Derive a variant
 *   with `client.newBuilder()`, it shares the pool. It has no HTTP cache on purpose.
 * - [Json]: the one configuration for reading and writing JSON.
 * - [HttpClient]: Ktor on top of that same [OkHttpClient], with JSON bodies, retries on server
 *   errors and a one line log per request. Compression needs no plugin, OkHttp handles gzip.
 */
val networkModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        HttpClient(OkHttp) {
            engine { preconfigured = get<OkHttpClient>() }

            install(ContentNegotiation) { json(get<Json>()) }

            install(HttpRequestRetry) {
                retryOnServerErrors(MAX_RETRIES)
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
