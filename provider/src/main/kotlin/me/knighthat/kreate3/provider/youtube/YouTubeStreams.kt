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

import android.util.Log
import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import java.util.Locale
import com.metrolist.innertubex.InnerTube as InnerTubeX

/**
 * Finds what to play with InnerTubeX, the library of Metrolist: it asks the clients YouTube plays
 * for, fastest first like the visionOS app, then the website's (`WEB_REMIX`) with a PO token from
 * [poTokens], and deciphers addresses for the clients that need it.
 *
 * Requests are never in the user's session. The library's player configuration isn't fetched
 * from GitHub, the scripts it ships with are used instead.
 *
 * @param client needs JSON content negotiation, the library sends and reads objects
 * @param poTokens makes PO tokens, `null` leaves out the clients that need one
 */
internal class YouTubeStreams(
    client: HttpClient,
    private val locale: () -> Locale,
    poTokens: TokenProvider? = null,
) {

    private val innerTube = InnerTubeX(client, logger = LOGGER)

    private val extractor: InnerTubeExtractor by lazy {
        val configs = RemotePlayerConfigStore(client, PlayerConfigRepository.disabled(), LOGGER)
        val cipher = YouTubeCipherService(client, configs, LOGGER)
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(client, innerTube, configs, LOGGER, cipher),
            cipherService = cipher,
            innerTube = innerTube,
            tokenProvider = poTokens ?: NO_PO_TOKENS,
            logger = LOGGER,
        )
    }

    /**
     * @param refused the [YouTubeStream.client]s whose streams were refused for this song, not
     * asked again
     * @return `null` when the song can't be played
     * @throws YouTubeException when YouTube can't be reached or the library failed
     */
    suspend fun stream(videoId: String, refused: Set<String> = emptySet()): YouTubeStream? {
        val locale = locale()
        innerTube.locale = YouTubeLocale(gl = locale.youTubeGl, hl = locale.youTubeHl)
        val extracted = try {
            extractor.extract(
                videoId = videoId,
                hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false),
                excludedClients = refused,
                audioQuality = AudioQuality.HIGH,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamResolveException) {
            if (e.reason == StreamResolveException.Reason.NETWORK)
                throw YouTubeException("Couldn't reach YouTube", cause = e)
            return null
        } catch (e: InnerTubeHttpException) {
            throw YouTubeException("YouTube answered ${e.status}", e.status.value, e)
        } catch (e: Exception) {
            throw YouTubeException("Couldn't find what to play on YouTube", cause = e)
        } catch (e: UnsatisfiedLinkError) {
            // QuickJS, which deciphers addresses, isn't built for every device
            throw YouTubeException("Couldn't decipher what to play on YouTube", cause = e)
        }
        return extracted?.toYouTubeStream()
    }

    private companion object {
        const val TAG = "InnerTubeX"

        val NO_PO_TOKENS = object : TokenProvider {
            override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?) =
                null
        }

        /** Warnings and errors only. The library leaves addresses, tokens and cookies out. */
        val LOGGER = InnerTubeLogger { event ->
            val details = event.details.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
            val message = event.message + details
            when (event.level) {
                InnerTubeLogLevel.WARN -> Log.w(TAG, message)
                InnerTubeLogLevel.ERROR -> Log.e(TAG, message)
                else -> Unit
            }
        }
    }
}

/** `null` for a stream in parts (SABR), which the player can't play. */
internal fun ExtractedStream.toYouTubeStream(): YouTubeStream? {
    if (sabrBootstrap != null) return null
    return YouTubeStream(
        url = audioUrl,
        headers = headers,
        mimeType = mimeType?.let { type -> codecs?.let { "$type; codecs=\"$it\"" } ?: type },
        bitrate = bitrate,
        contentLength = contentLengthBytes,
        loudnessDb = loudnessDb,
        expiresAt = expiresAt,
        rangeSize = rangeChunkSizeBytes.takeIf { requireBoundedRange },
        client = profileId,
        itag = itag,
    )
}
