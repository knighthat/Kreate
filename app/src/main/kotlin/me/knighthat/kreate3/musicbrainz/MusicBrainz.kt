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

package me.knighthat.kreate3.musicbrainz

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.knighthat.kreate3.BuildConfig
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A recording [MusicBrainz.search] found.
 *
 * @property artists the credit as MusicBrainz writes it, like `A feat. B`
 * @property firstReleased `YYYY`, `YYYY-MM` or `YYYY-MM-DD`
 * @property disambiguation what tells it from recordings of the same name, like `live, 1986`
 * @property releases how many releases it is on
 * @property score how well it matches the search, 0 to 100
 */
data class MusicBrainzMatch(
    val id: String,
    val title: String,
    val artists: String?,
    val length: Duration?,
    val firstReleased: String?,
    val disambiguation: String?,
    val releases: Int,
    val score: Int,
)

/**
 * One recording of [MusicBrainz], with what is known about it.
 *
 * @property isrcs the codes it is sold under
 * @property genres its genres, then the tags people gave it, the most voted first
 * @property releases the albums, singles and compilations it is on, the oldest first
 */
data class MusicBrainzRecording(
    val id: String,
    val title: String,
    val artists: String?,
    val length: Duration?,
    val firstReleased: String?,
    val disambiguation: String?,
    val isrcs: List<String>,
    val genres: List<String>,
    val releases: List<MusicBrainzRelease>,
) {

    /** The recording's page on the MusicBrainz site. */
    val url: String get() = "https://musicbrainz.org/recording/$id"
}

/**
 * @property type the kind of release, like `Album` or `Single`
 * @property country where it came out, an ISO code or `XW` for the whole world
 */
data class MusicBrainzRelease(
    val id: String,
    val title: String,
    val date: String?,
    val country: String?,
    val type: String?,
    val status: String?,
)

/**
 * [MusicBrainz](https://musicbrainz.org), an open database of music anyone can edit. It is asked
 * at most once a second, all callers together, as its rules ask; a faster client is blocked.
 */
class MusicBrainz(private val client: HttpClient, private val json: Json) {

    private val gate = Mutex()
    private var lastRequest: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * The recordings named [title] by [artist], the best matches first, as MusicBrainz ranks
     * them. Words are matched as a phrase first, one by one when that finds nothing.
     *
     * @throws IOException when MusicBrainz can't be reached or answers with an error
     */
    suspend fun search(title: String, artist: String?): List<MusicBrainzMatch> =
        withContext(Dispatchers.Default) {
            search(query(title, artist, phrase = true))
                .ifEmpty { search(query(title, artist, phrase = false)) }
        }

    /**
     * The recording [search] finds that is most likely the song: of the best scored ones, a
     * recording as long as [length] (within a few seconds), not live unless the title says so,
     * on the most releases, released first.
     *
     * @return `null` when nothing was found
     */
    suspend fun identify(title: String, artist: String?, length: Duration?): MusicBrainzMatch? =
        search(title, artist).best(title, length)

    /**
     * Everything about the recording [id].
     *
     * @return `null` when MusicBrainz has no such recording
     * @throws IOException when MusicBrainz can't be reached or answers with an error
     */
    suspend fun recording(id: String): MusicBrainzRecording? = withContext(Dispatchers.Default) {
        val body = request("recording/${id.encodeURLPathPart()}") {
            parameter("inc", "artist-credits+releases+release-groups+isrcs+genres+tags")
        } ?: return@withContext null
        json.parseToJsonElement(body).jsonObject.toRecording()
    }

    private suspend fun search(query: String): List<MusicBrainzMatch> {
        val body = request("recording") {
            parameter("query", query)
            parameter("limit", SEARCH_LIMIT)
        } ?: return emptyList()
        return json.parseToJsonElement(body).jsonObject.array("recordings")
            .map { it.jsonObject.toMatch() }
    }

    /** @return `null` when MusicBrainz answered 404 */
    private suspend fun request(
        path: String,
        block: HttpRequestBuilder.() -> Unit,
    ): String? = gate.withLock {
        lastRequest?.elapsedNow()?.let { delay(REQUEST_INTERVAL - it) }
        try {
            val response = client.get("$BASE_URL/$path") {
                // MusicBrainz blocks clients that don't name themselves
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
                parameter("fmt", "json")
                block()
            }
            when {
                response.status == HttpStatusCode.NotFound -> null
                !response.status.isSuccess() -> throw IOException("MusicBrainz answered ${response.status}")
                else -> response.bodyAsText()
            }
        } finally {
            lastRequest = TimeSource.Monotonic.markNow()
        }
    }

    private fun JsonObject.toMatch() = MusicBrainzMatch(
        id = string("id").orEmpty(),
        title = string("title").orEmpty(),
        artists = credit(),
        length = this["length"]?.jsonPrimitive?.longOrNull?.milliseconds,
        firstReleased = string("first-release-date"),
        disambiguation = string("disambiguation"),
        releases = array("releases").size,
        score = this["score"]?.jsonPrimitive?.intOrNull ?: 0,
    )

    private fun JsonObject.toRecording(): MusicBrainzRecording {
        val releases = array("releases").map { element ->
            val release = element.jsonObject
            MusicBrainzRelease(
                id = release.string("id").orEmpty(),
                title = release.string("title").orEmpty(),
                date = release.string("date"),
                country = release.string("country"),
                type = (release["release-group"] as? JsonObject)?.string("primary-type"),
                status = release.string("status"),
            )
        }.sortedWith(compareBy(nullsLast()) { it.date })

        return MusicBrainzRecording(
            id = string("id").orEmpty(),
            title = string("title").orEmpty(),
            artists = credit(),
            length = this["length"]?.jsonPrimitive?.longOrNull?.milliseconds,
            firstReleased = string("first-release-date")
                ?: releases.firstNotNullOfOrNull { it.date },
            disambiguation = string("disambiguation"),
            isrcs = array("isrcs").mapNotNull { it.jsonPrimitive.contentOrNull },
            genres = (namesByVotes("genres") + namesByVotes("tags")).distinct(),
            releases = releases,
        )
    }

    /** The artist credit with its join phrases, like `A feat. B`. */
    private fun JsonObject.credit(): String? =
        array("artist-credit").joinToString("") {
            val part = it.jsonObject
            part.string("name").orEmpty() + part.string("joinphrase").orEmpty()
        }.ifBlank { null }

    private fun JsonObject.namesByVotes(key: String): List<String> =
        array(key).map { it.jsonObject }
            .sortedByDescending { it["count"]?.jsonPrimitive?.intOrNull ?: 0 }
            .mapNotNull { it.string("name") }

    private fun JsonObject.array(key: String): List<JsonElement> =
        (this[key] as? JsonArray).orEmpty()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private const val BASE_URL = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT =
            "Kreate/${BuildConfig.VERSION_NAME} ( https://github.com/knighthat/Kreate )"
        private const val SEARCH_LIMIT = 25
        private val REQUEST_INTERVAL = 1.seconds

        /** How far off a recording's length may be from the song's to still be it. */
        private val LENGTH_TOLERANCE = 5.seconds

        /** How far below the best score a match is still considered. */
        private const val SCORE_MARGIN = 10

        /** Characters Lucene reads as syntax, escaped in words searched one by one. */
        private val LUCENE_SYNTAX = Regex("""[+\-&|!(){}\[\]^"~*?:\\/]""")

        private fun query(title: String, artist: String?, phrase: Boolean): String {
            fun terms(text: String) =
                if (phrase) "\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                else "(${text.replace(LUCENE_SYNTAX) { "\\" + it.value }})"

            val recording = "recording:${terms(title.trim())}"
            val credit = artist?.trim()?.takeIf { it.isNotEmpty() } ?: return recording
            return "$recording AND artist:${terms(credit)}"
        }

        internal fun List<MusicBrainzMatch>.best(
            title: String,
            length: Duration?,
        ): MusicBrainzMatch? {
            val top = maxOfOrNull { it.score } ?: return null
            var candidates = filter { it.score >= top - SCORE_MARGIN }
            if (length != null && length.isPositive())
                candidates.filter {
                    it.length != null && (it.length - length).absoluteValue <= LENGTH_TOLERANCE
                }.takeIf { it.isNotEmpty() }?.let { candidates = it }
            if (!title.contains("live", ignoreCase = true))
                candidates.filterNot {
                    it.disambiguation?.contains(
                        "live",
                        ignoreCase = true
                    ) == true
                }
                    .takeIf { it.isNotEmpty() }?.let { candidates = it }
            return candidates.sortedWith(
                compareByDescending<MusicBrainzMatch> { it.releases }
                    .thenBy(nullsLast()) { it.firstReleased }
            ).firstOrNull()
        }
    }
}
