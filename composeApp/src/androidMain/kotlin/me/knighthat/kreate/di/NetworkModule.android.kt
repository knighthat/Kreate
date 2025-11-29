package me.knighthat.kreate.di

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp


actual val networkEngine: HttpClientEngineFactory<HttpClientEngineConfig> = OkHttp