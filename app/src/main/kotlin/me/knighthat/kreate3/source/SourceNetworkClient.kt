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
 */
internal class SourceNetworkClient(
    app: HttpClient,
    private val youTube: YouTubeProvider,
) : NetworkClient {

    private val app = app.asNetworkClient()

    /** YouTube's client as Coil's, with the client it wraps, made again when that changes. */
    @Volatile
    private var youTubeClient: Pair<HttpClient, NetworkClient>? = null

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        val host = request.url.toUri().host?.lowercase()
        val client = if (host != null && isYouTubeHost(host)) youTube() else app
        return client.executeRequest(request, block)
    }

    private suspend fun youTube(): NetworkClient {
        val client = youTube.httpClient()
        youTubeClient?.takeIf { it.first === client }?.let { return it.second }
        return client.asNetworkClient().also { youTubeClient = client to it }
    }
}
