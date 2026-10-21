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

package me.knighthat.kreate3.provider.youtube

import io.ktor.client.HttpClient
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.HttpClientFactory
import me.knighthat.kreate3.provider.SourceProxy
import me.knighthat.kreate3.provider.proxy
import me.knighthat.kreate3.provider.sourceProxy
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * The proxy YouTube's requests go through now, see [Preferences.YOUTUBE_PROXY_ENABLED].
 */
suspend fun youTubeProxy(): SourceProxy =
    sourceProxy(
        Preferences.YOUTUBE_PROXY_ENABLED,
        Preferences.YOUTUBE_PROXY_TYPE,
        Preferences.YOUTUBE_PROXY_HOST,
        Preferences.YOUTUBE_PROXY_PORT,
        Secrets.YOUTUBE_PROXY_USERNAME,
        Secrets.YOUTUBE_PROXY_PASSWORD,
    )

/**
 * Whether [host] is one of YouTube's, which its pages, streams and pictures come from, so what
 * reads them outside the provider goes through YouTube's client too.
 */
fun isYouTubeHost(host: String): Boolean =
    YOUTUBE_DOMAINS.any { host == it || host.endsWith(".$it") }

private val YOUTUBE_DOMAINS =
    listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com", "googleusercontent.com")

/**
 * The HTTP clients of YouTube, and only of it, so its proxy never reaches other requests. Without
 * a proxy they are the app's, sharing its connections.
 *
 * The clients are built again whenever the proxy changes, with new connections, so a connection
 * of the old proxy, or of none, is never used again.
 */
internal class YouTubeClients(
    private val base: OkHttpClient,
    private val factory: HttpClientFactory,
    private val proxy: suspend () -> SourceProxy = ::youTubeProxy,
) {

    private class Clients(val proxy: SourceProxy, val okHttp: OkHttpClient, val ktor: HttpClient)

    private var clients: Clients? = null

    /**
     * The client for the proxy now. A replaced client is not closed, requests on it may still
     * run, and closing would stop the dispatcher it shares with [base].
     */
    suspend fun current(): HttpClient = clients(proxy()).ktor

    /** The OkHttp client under [current], for what reads YouTube without Ktor. */
    suspend fun currentOkHttp(): OkHttpClient = clients(proxy()).okHttp

    @Synchronized
    private fun clients(wanted: SourceProxy): Clients {
        clients?.takeIf { it.proxy == wanted }?.let { return it }

        val okHttp = base.newBuilder()
            .apply { if (wanted != SourceProxy.Off) connectionPool(ConnectionPool()) }
            .proxy(SOURCE, wanted)
            .build()
        return Clients(wanted, okHttp, factory.create(okHttp)).also { clients = it }
    }

    private companion object {
        const val SOURCE = "youtube"
    }
}
