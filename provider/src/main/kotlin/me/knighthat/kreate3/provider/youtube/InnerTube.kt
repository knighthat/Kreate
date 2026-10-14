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
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale

/**
 * A request to YouTube failed or got an answer that can't be read.
 *
 * @property status HTTP status of the answer, `null` when there was none
 */
class YouTubeException(
    message: String,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * InnerTube, the API the YouTube Music website talks to, as the website (`WEB_REMIX`) without a
 * sign in. It only reads the catalog, getting what to play needs more and is not done here.
 *
 * The first answer gives a visitor id, which is sent with every request after it: some lists,
 * like the home page, only hand out their next part to the same visitor. It is kept for the run
 * of the app only.
 *
 * @param locale gives the language of the texts and the country of the catalog, read at each
 * request
 */
internal class InnerTube(
    private val client: HttpClient,
    private val json: Json,
    private val locale: () -> Locale,
) {

    @Volatile
    private var visitorData: String? = null

    suspend fun browse(browseId: String, params: String? = null): JsonObject =
        post("browse") {
            put("browseId", browseId)
            params?.let { put("params", it) }
        }

    suspend fun search(query: String, params: String?): JsonObject =
        post("search") {
            put("query", query)
            params?.let { put("params", it) }
        }

    /**
     * The queue of [videoId], or of [playlistId] when it is played, with the tabs of the player
     * that lead to the lyrics and related items.
     */
    suspend fun next(videoId: String?, playlistId: String?): JsonObject =
        post("next") {
            videoId?.let { put("videoId", it) }
            playlistId?.let { put("playlistId", it) }
            putQueueOptions()
        }

    /** The next part of a list of [endpoint], which must be the one that gave [token]. */
    suspend fun continuation(
        endpoint: Endpoint,
        token: String,
        playlistId: String? = null
    ): JsonObject =
        post(endpoint.path) {
            put("continuation", token)
            if (endpoint == Endpoint.NEXT) {
                playlistId?.let { put("playlistId", it) }
                putQueueOptions()
            }
        }

    suspend fun suggestions(input: String): JsonObject =
        post("music/get_search_suggestions") { put("input", input) }

    private fun JsonObjectBuilder.putQueueOptions() {
        put("isAudioOnly", true)
        put("enablePersistentPlaylistPanel", true)
        put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
    }

    /**
     * @throws YouTubeException when the request fails or the answer is not JSON
     */
    private suspend fun post(path: String, body: JsonObjectBuilder.() -> Unit): JsonObject {
        val visitor = visitorData
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    val locale = locale()
                    put("hl", locale.language.ifEmpty { "en" } +
                            locale.country.takeIf { it.isNotEmpty() }?.let { "-$it" }.orEmpty())
                    put("gl", locale.country.ifEmpty { "US" })
                    visitor?.let { put("visitorData", it) }
                }
            }
            body()
        }

        val response = try {
            client.post("$BASE_URL/$path") {
                parameter("prettyPrint", false)
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Origin, ORIGIN)
                header(HttpHeaders.Referrer, "$ORIGIN/")
                header("X-YouTube-Client-Name", CLIENT_ID)
                header("X-YouTube-Client-Version", CLIENT_VERSION)
                visitor?.let { header("X-Goog-Visitor-Id", it) }
                setBody(TextContent(payload.toString(), ContentType.Application.Json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw YouTubeException("Couldn't reach YouTube", cause = e)
        }

        val text = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw YouTubeException("Couldn't read the answer of YouTube", response.status.value, e)
        }
        if (!response.status.isSuccess())
            throw YouTubeException("YouTube answered ${response.status}", response.status.value)

        val answer = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw YouTubeException(
                "YouTube answered something else than JSON",
                response.status.value,
                e
            )
        }
        if (visitor == null)
            answer.path("responseContext", "visitorData").asString?.let { visitorData = it }
        return answer
    }

    enum class Endpoint(val path: String) {
        BROWSE("browse"),
        SEARCH("search"),
        NEXT("next"),
    }

    private companion object {
        const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        const val ORIGIN = "https://music.youtube.com"
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"

        /** The website's version on 2026-10-13. YouTube keeps answering older versions for long. */
        const val CLIENT_VERSION = "1.20261011.16.00"

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/140.0.0.0 Safari/537.36"
    }
}
