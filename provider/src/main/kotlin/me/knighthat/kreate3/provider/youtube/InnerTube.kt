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

import com.metrolist.innertubex.extraction.TokenProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.time.Clock

/** The language YouTube answers in, like `en-US`. */
internal val Locale.youTubeHl: String
    get() = language.ifEmpty { "en" } + country.takeIf { it.isNotEmpty() }?.let { "-$it" }.orEmpty()

/** The country of the catalog YouTube answers from. */
internal val Locale.youTubeGl: String
    get() = country.ifEmpty { "US" }

/**
 * A request to YouTube failed or got an answer that can't be read.
 *
 * @property status HTTP status of the answer, `null` when there was none
 * @property signedOut whether it failed because it needs a signed in user and there is none, or
 * the session the user signed in to has ended
 */
class YouTubeException(
    message: String,
    val status: Int? = null,
    cause: Throwable? = null,
    val signedOut: Boolean = false,
) : Exception(message, cause)

/**
 * The YouTube session of a signed in user, from the cookies of the website, acting as one of the
 * channels of their Google account.
 *
 * @param cookies the cookies as a `Cookie` header, `name=value` pairs split by `;`
 * @param pageId page id of the channel the user acts as, `null` for the account's own
 */
internal class YouTubeSession private constructor(
    val cookies: String,
    private val values: Map<String, String>,
    val pageId: String?,
) {

    /** The cookie the requests are signed with. */
    val sapisid: String = checkNotNull(values["SAPISID"] ?: values["__Secure-3PAPISID"])

    /** Tells sessions apart, a channel of the same account is another one. */
    val id: String = "$sapisid|${pageId.orEmpty()}"

    /**
     * The `Authorization` header of a request sent at [epochSeconds]: a hash of the time, the
     * session and the origin for each kind of session cookie there is, like the website sends.
     */
    fun authorization(epochSeconds: Long, origin: String): String =
        listOf(
            "SAPISIDHASH" to sapisid,
            "SAPISID1PHASH" to values["__Secure-1PAPISID"],
            "SAPISID3PHASH" to values["__Secure-3PAPISID"],
        ).mapNotNull { (scheme, sid) ->
            sid?.let { "$scheme ${epochSeconds}_${sha1("$epochSeconds $it $origin")}" }
        }.joinToString(" ")

    companion object {

        /**
         * @param pageId page id of the channel to act as, empty or `null` for the account's own
         * @return `null` when [cookies] are not of a signed in session
         */
        fun of(cookies: String, pageId: String? = null): YouTubeSession? {
            val values = cookies.split(';').mapNotNull { pair ->
                val (name, value) = pair.split('=', limit = 2).takeIf { it.size == 2 }
                    ?: return@mapNotNull null
                name.trim().takeIf { it.isNotEmpty() }?.let { it to value.trim() }
            }.toMap()
            if (values["SAPISID"] == null && values["__Secure-3PAPISID"] == null) return null
            return YouTubeSession(cookies.trim(), values, pageId?.takeIf { it.isNotBlank() })
        }

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/**
 * InnerTube, the API the YouTube Music website talks to, as the website (`WEB_REMIX`). While the
 * user is signed in, every request is sent in their session, signed like the website signs it,
 * and answers are their own, like a personal home page. What to play is found by InnerTubeX,
 * see [stream].
 *
 * The first answer gives a visitor id, which is sent with every request after it: some lists,
 * like the home page, only hand out their next part to the same visitor. It is kept for the run
 * of the app only, and replaced when another session starts.
 *
 * @param locale gives the language of the texts and the country of the catalog, read at each
 * request
 * @param cookies gives the cookies of the signed in session as a `Cookie` header, empty while
 * nobody is signed in. Read at each request, so signing in or out applies at once.
 * @param pageId gives the page id of the channel the signed in user acts as, empty for their
 * account's own. Read at each request too.
 * @param poTokens makes the PO tokens [stream] may need, `null` for none
 */
internal class InnerTube(
    private val client: HttpClient,
    private val json: Json,
    private val locale: () -> Locale,
    private val cookies: suspend () -> String = { "" },
    private val clock: Clock = Clock.System,
    private val pageId: suspend () -> String = { "" },
    poTokens: TokenProvider? = null,
) {

    /** The visitor id YouTube gave, with the [YouTubeSession.id] it was given to. */
    private data class Visitor(val session: String?, val data: String)

    @Volatile
    private var visitor: Visitor? = null

    private val streams = YouTubeStreams(client, locale, poTokens)

    /**
     * The audio to play of [videoId], never asked in the session.
     *
     * @param refused see [YouTubeStreams.stream]
     * @return `null` when it can't be played
     * @throws YouTubeException when YouTube can't be reached or what to play couldn't be found
     */
    suspend fun stream(videoId: String, refused: Set<String> = emptySet()): YouTubeStream? =
        streams.stream(videoId, refused)

    /** The session requests are sent in now, `null` while nobody is signed in. */
    suspend fun session(): YouTubeSession? = YouTubeSession.of(cookies(), pageId())

    /**
     * Every channel of the signed in user's account. Asked in the session, whichever channel it
     * acts as.
     */
    suspend fun accountsList(): JsonObject = post("account/accounts_list", signedIn = true) {}

    /**
     * @param signedIn whether the page is the user's own, like their library. It is not asked
     * without a session, and an answer that is not in the session fails.
     */
    suspend fun browse(
        browseId: String,
        params: String? = null,
        signedIn: Boolean = false
    ): JsonObject =
        post("browse", signedIn) {
            put("browseId", browseId)
            params?.let { put("params", it) }
        }

    /** The signed in user's name and picture. */
    suspend fun accountMenu(): JsonObject = post("account/account_menu", signedIn = true) {}

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

    /**
     * The next part of a list of [endpoint], which must be the one that gave [token].
     *
     * @param signedIn see [browse]
     */
    suspend fun continuation(
        endpoint: Endpoint,
        token: String,
        playlistId: String? = null,
        signedIn: Boolean = false,
    ): JsonObject =
        post(endpoint.path, signedIn) {
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
     * @param signedIn see [browse]
     * @throws YouTubeException when the request fails or the answer is not JSON, or with
     * [YouTubeException.signedOut] when [signedIn] and nobody is signed in or the session ended
     */
    private suspend fun post(
        path: String,
        signedIn: Boolean = false,
        body: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val session = session()
        if (signedIn && session == null)
            throw YouTubeException("Nobody is signed in to YouTube", signedOut = true)
        // A visitor id belongs to the session it was given to
        val visitor = this.visitor?.takeIf { it.session == session?.id }?.data
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    val locale = locale()
                    put("hl", locale.youTubeHl)
                    put("gl", locale.youTubeGl)
                    visitor?.let { put("visitorData", it) }
                }
                session?.pageId?.let { putJsonObject("user") { put("onBehalfOfUser", it) } }
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
                session?.let {
                    header(HttpHeaders.Cookie, it.cookies)
                    header(
                        HttpHeaders.Authorization,
                        it.authorization(clock.now().epochSeconds, ORIGIN)
                    )
                    header("X-Goog-AuthUser", "0")
                    header("X-Origin", ORIGIN)
                    it.pageId?.let { pageId -> header("X-Goog-PageId", pageId) }
                }
                setBody(TextContent(payload.toString(), ContentType.Application.Json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw YouTubeException("Couldn't reach YouTube", cause = e)
        }

        // Decoding an answer takes long, they are large, never on the caller's thread, which may be
        // the main one
        val answer = withContext(Dispatchers.IO) {
            val text = try {
                response.bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw YouTubeException(
                    "Couldn't read the answer of YouTube",
                    response.status.value,
                    e
                )
            }
            if (!response.status.isSuccess())
                throw YouTubeException("YouTube answered ${response.status}", response.status.value)

            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw YouTubeException(
                    "YouTube answered something else than JSON",
                    response.status.value,
                    e
                )
            }
        }
        if (signedIn && !answer.isSignedIn())
            throw YouTubeException(
                "The YouTube session ended, sign in again",
                response.status.value,
                signedOut = true,
            )
        if (visitor == null)
            answer.path("responseContext", "visitorData").asString
                ?.let { this.visitor = Visitor(session?.id, it) }
        return answer
    }

    /**
     * Whether YouTube answered in the session, which it tells among the values it reports. An
     * answer that doesn't tell is taken as signed in.
     */
    private fun JsonObject.isSignedIn(): Boolean =
        path("responseContext", "serviceTrackingParams").asArray.orEmpty()
            .flatMap { it.path("params").asArray.orEmpty() }
            .firstOrNull { it.path("key").asString in SIGNED_IN_KEYS }
            ?.path("value")?.asString != "0"

    enum class Endpoint(val path: String) {
        BROWSE("browse"),
        SEARCH("search"),
        NEXT("next"),
    }

    private companion object {
        /** Names under which an answer reports whether it is in a session, `0` when it isn't. */
        val SIGNED_IN_KEYS = setOf("logged_in", "yt_li")

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
