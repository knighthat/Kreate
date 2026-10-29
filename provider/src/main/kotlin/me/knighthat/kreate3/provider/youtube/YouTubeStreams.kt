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
import me.knighthat.kreate3.constant.StreamQuality
import java.util.Locale
import com.metrolist.innertubex.InnerTube as InnerTubeX

/**
 * Finds what to play with InnerTubeX, the library of Metrolist: it asks the clients YouTube plays
 * for, fastest first like the visionOS app, then the website's (`WEB_REMIX`) with a PO token from
 * [poTokens], and deciphers addresses for the clients that need it.
 *
 * Requests are in the user's session only for [StreamQuality.ORIGINAL], the only quality an account
 * changes, see [stream]. The library's player configuration isn't fetched from GitHub, the scripts
 * it ships with are used instead.
 *
 * @param client needs JSON content negotiation, the library sends and reads objects
 * @param locale gives the language of the texts, read at each extraction
 * @param region gives the country of the catalog, read at each extraction
 * @param poTokens makes PO tokens, `null` leaves out the clients that need one
 */
internal class YouTubeStreams(
    client: HttpClient,
    private val locale: () -> Locale,
    private val region: () -> String,
    poTokens: TokenProvider? = null,
) {

    private val configs by lazy {
        RemotePlayerConfigStore(client, PlayerConfigRepository.disabled(), LOGGER)
    }
    private val cipher by lazy { YouTubeCipherService(client, configs, LOGGER) }

    private val innerTube = InnerTubeX(client, logger = LOGGER)
    private val extractor by lazy { extractor(client, innerTube, poTokens) }

    /** Asks in the user's session, its visitor id is another than the signed out one's. */
    private val sessionInnerTube = InnerTubeX(client, logger = LOGGER)
    private val sessionExtractor by lazy { extractor(client, sessionInnerTube, poTokens) }

    private fun extractor(client: HttpClient, innerTube: InnerTubeX, poTokens: TokenProvider?) =
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(client, innerTube, configs, LOGGER, cipher),
            cipherService = cipher,
            innerTube = innerTube,
            tokenProvider = poTokens ?: NO_PO_TOKENS,
            logger = LOGGER,
        )

    /**
     * @param refused the [YouTubeStream.client]s whose streams were refused for this song, not
     * asked again
     * @param cookies the cookies of the signed in session, only used for
     * [StreamQuality.ORIGINAL]. The library is told the account has Premium, which it can't find
     * out: an account without it gets what [StreamQuality.HIGH] gets, maybe after a refusal.
     * @return `null` when the song can't be played
     * @throws YouTubeException when YouTube can't be reached or the library failed
     */
    suspend fun stream(
        videoId: String,
        refused: Set<String> = emptySet(),
        quality: StreamQuality = StreamQuality.HIGH,
        cookies: String? = null,
    ): YouTubeStream? {
        val inSession = quality == StreamQuality.ORIGINAL && !cookies.isNullOrBlank()
        val stream = extract(videoId, refused, quality.audioQuality, inSession, cookies)
        // Nearly every video has the MP4 audio of 128 kbps, the best is played without it
        if (stream == null && quality == StreamQuality.MEDIUM)
            return extract(videoId, refused, AudioQuality.HIGH, false, null)
        return stream
    }

    private suspend fun extract(
        videoId: String,
        refused: Set<String>,
        audioQuality: AudioQuality,
        inSession: Boolean,
        cookies: String?,
    ): YouTubeStream? {
        val innerTube = if (inSession) sessionInnerTube else innerTube
        innerTube.locale = YouTubeLocale(gl = region(), hl = locale().youTubeHl)
        // The same cookies change nothing, other ones start another session in the library
        if (inSession) innerTube.cookie = cookies
        val hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false)
        val extracted = try {
            (if (inSession) sessionExtractor else extractor).extract(
                videoId = videoId,
                hints = if (inSession) hints.withPremium() else hints,
                excludedClients = refused,
                audioQuality = audioQuality,
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

        /**
         * The library's choices: the smallest MP4 (48 kbps AAC), the best MP4 (128 kbps AAC
         * signed out) and the highest bitrate (160 kbps Opus, 256 kbps AAC with Premium).
         */
        val StreamQuality.audioQuality: AudioQuality
            get() = when (this) {
                StreamQuality.LOW -> AudioQuality.LOW
                StreamQuality.MEDIUM -> AudioQuality.MP4
                StreamQuality.HIGH, StreamQuality.ORIGINAL -> AudioQuality.HIGH
            }

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
