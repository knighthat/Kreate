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

package me.knighthat.kreate3.shazam

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val TAG = "Shazam"

/** Least time between two lookups, so holding again and again doesn't hammer the server. */
private val REQUEST_GAP = 3.seconds

/**
 * A song Shazam named.
 *
 * @param album from the song's metadata, `null` when Shazam has none
 * @param cover address of the biggest cover Shazam has
 * @param isrc the recording's code, to find the same recording on other services
 * @param explicit Shazam marks the song explicit
 * @param shazamUrl the song's page on Shazam, the one it shares
 * @param youTubeMusicSearch Shazam's search for the song on YouTube Music, a
 *   `music.youtube.com/search?q=…` address
 */
data class ShazamTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val cover: String?,
    val isrc: String?,
    val explicit: Boolean,
    val shazamUrl: String?,
    val youTubeMusicSearch: String?,
)

/** Shazam's server couldn't be asked, or answered with an error. */
class ShazamException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Names songs from recorded sound with Shazam's undocumented server, the one its Android app asks.
 * The app records; this turns the sound into Shazam's signature and looks it up.
 */
class Shazam(private val client: HttpClient, private val json: Json) {

    private val gate = Mutex()
    private var lastRequest: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * Names the song in [pcm]. Main-safe: the signature is made on [Dispatchers.Default].
     * Only the first 12 s or so are used once they hold enough of the song.
     *
     * @param pcm mono 16-bit samples
     * @param sampleRate of [pcm], in samples per second
     * @param locale language and country Shazam answers for, like the titles it picks
     * @return the song, `null` when nothing matched or the sound was too quiet to look up
     * @throws ShazamException when the server couldn't be asked or answered with an error
     */
    suspend fun recognize(pcm: ShortArray, sampleRate: Int, locale: Locale): ShazamTrack? {
        val signature = withContext(Dispatchers.Default) { Signature.of(resample(pcm, sampleRate)) }
        // Silence or noise has nothing to match, not worth a request
        if (signature.peaks == 0) {
            Logger.d(tag = TAG) { "No peaks in ${signature.sampleMillis} ms of sound, nothing to look up" }
            return null
        }
        Logger.d(tag = TAG) { "Looking up ${signature.peaks} peaks of ${signature.sampleMillis} ms" }

        val body = withContext(Dispatchers.Default) { requestBody(signature).toString() }
        val text = gate.withLock {
            lastRequest?.let { delay(REQUEST_GAP - it.elapsedNow()) }
            try {
                send(body, locale)
            } finally {
                lastRequest = TimeSource.Monotonic.markNow()
            }
        } ?: return null

        return withContext(Dispatchers.Default) {
            try {
                parse(json.parseToJsonElement(text).jsonObject)
            } catch (e: Exception) {
                throw ShazamException("Shazam's answer couldn't be read", e)
            }
        }
    }

    /** @return the answer, `null` for Shazam's "no match" */
    private suspend fun send(body: String, locale: Locale): String? {
        val language = locale.language.ifEmpty { "en" }
        val country = locale.country.ifEmpty { "US" }
        val response = try {
            client.post(
                "$BASE_URL/$language/$country/android/-/tag/" +
                        "${UUID.randomUUID().toString().uppercase()}/${UUID.randomUUID()}"
            ) {
                url.parameters.appendAll(QUERY)
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.ContentLanguage, "${language}_$country")
                setBody(TextContent(body, ContentType.Application.Json))
            }
        } catch (e: IOException) {
            throw ShazamException("Shazam couldn't be reached", e)
        }

        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess())
            throw ShazamException("Shazam answered ${response.status}")
        return response.bodyAsText()
    }

    private fun requestBody(signature: Signature): JsonObject {
        val now = System.currentTimeMillis()
        return buildJsonObject {
            putJsonObject("signature") {
                put("uri", signature.uri)
                put("samplems", signature.sampleMillis)
                put("timestamp", now)
            }
            put("timestamp", now)
            put("timezone", TimeZone.getDefault().id)
        }
    }

    internal companion object {

        private const val BASE_URL = "https://amp.shazam.com/discovery/v5"

        private val QUERY = io.ktor.http.parameters {
            append("sync", "true")
            append("webv3", "true")
            append("sampling", "true")
            append("connected", "")
            append("shazamapiversion", "v3")
            append("sharehub", "true")
            append("video", "v3")
        }

        /** The device's own, like Shazam's app sends; a fixed one where there's none, like in tests. */
        private val USER_AGENT: String =
            System.getProperty("http.agent") ?: "Dalvik/2.1.0 (Linux; U; Android 14)"

        /** @return the song in Shazam's answer, `null` when it matched nothing */
        internal fun parse(answer: JsonObject): ShazamTrack? {
            val track = answer["track"] as? JsonObject ?: return null
            val title = track.string("title") ?: return null
            val metadata = track["sections"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .firstOrNull { it.string("type") == "SONG" }
                ?.get("metadata")?.jsonArray.orEmpty()
                .map { it.jsonObject }
            val images = track["images"] as? JsonObject
            val hub = track["hub"] as? JsonObject
            val youTubeMusic = hub?.get("providers")?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .firstOrNull { it.string("type") == "YOUTUBEMUSIC" }
            return ShazamTrack(
                title = title,
                artist = track.string("subtitle").orEmpty(),
                album = metadata.firstOrNull { it.string("title") == "Album" }?.string("text"),
                cover = images?.string("coverarthq") ?: images?.string("coverart"),
                isrc = track.string("isrc"),
                explicit = hub?.get("explicit")?.jsonPrimitive?.booleanOrNull == true,
                shazamUrl = (track["share"] as? JsonObject)?.string("href") ?: track.string("url"),
                youTubeMusicSearch = youTubeMusic?.get("actions")?.jsonArray.orEmpty()
                    .firstNotNullOfOrNull { (it as? JsonObject)?.string("uri") },
            )
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
