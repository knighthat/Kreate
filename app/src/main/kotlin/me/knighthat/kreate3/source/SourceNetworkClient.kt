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

package me.knighthat.kreate3.source

import androidx.core.net.toUri
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.ktor3.asNetworkClient
import io.ktor.client.HttpClient
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.isYouTubeHost

/**
 * Loads pictures with the client of the source they come from: YouTube's through its proxy, see
 * [YouTubeProvider.httpClient], any other with the [app]'s client.
 *
 * A picture on `ytimg.com` that answered 404 isn't asked for again while the app runs, it answers
 * 404 at once: YouTube doesn't have it, like the wide frame of a video without one, and asking
 * again gets the same answer. Requests already on their way when the first 404 comes still go.
 */
internal class SourceNetworkClient(
    app: HttpClient,
    private val youTube: YouTubeProvider,
) : NetworkClient {

    private val app = app.asNetworkClient()

    /** YouTube's client as Coil's, with the client it wraps, made again when that changes. */
    @Volatile
    private var youTubeClient: Pair<HttpClient, NetworkClient>? = null

    /**
     * Addresses of `ytimg.com` pictures that answered 404, the oldest forgotten past
     * [MISSING_LIMIT].
     */
    private val missing = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) =
            size > MISSING_LIMIT
    }

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        val url = request.url
        val host = url.toUri().host?.lowercase()
        if (host == null || !isYouTubeHost(host)) return app.executeRequest(request, block)
        if (!isPictureHost(host)) return youTube().executeRequest(request, block)

        if (synchronized(missing) { url in missing }) return block(NetworkResponse(NOT_FOUND))
        return youTube().executeRequest(request) { response ->
            if (response.code == NOT_FOUND) synchronized(missing) { missing[url] = Unit }
            block(response)
        }
    }

    private suspend fun youTube(): NetworkClient {
        val client = youTube.httpClient()
        youTubeClient?.takeIf { it.first === client }?.let { return it.second }
        return client.asNetworkClient().also { youTubeClient = client to it }
    }

    private fun isPictureHost(host: String): Boolean =
        host == PICTURE_DOMAIN || host.endsWith(".$PICTURE_DOMAIN")

    private companion object {

        const val PICTURE_DOMAIN = "ytimg.com"
        const val NOT_FOUND = 404
        const val MISSING_LIMIT = 1000
    }
}
