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

package me.knighthat.kreate3.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate3.database.model.LyricsSync
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [LRCLIB](https://lrclib.net), a free database of lyrics anyone can add to: plain or timed per
 * line, as LRC. A few are timed per word, as enhanced LRC.
 */
class LrcLibSource(private val client: HttpClient, private val json: Json) : LyricsSource {

    override val id: String = ID
    override val name: String = "LRCLIB"

    override suspend fun find(query: LyricsQuery): FoundLyrics? = withContext(Dispatchers.Default) {
        val response = client.get("$BASE_URL/api/search") {
            // LRCLIB asks clients to name themselves
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("track_name", query.title)
            query.artists.firstOrNull()?.let { parameter("artist_name", it) }
            query.album?.let { parameter("album_name", it) }
        }
        if (!response.status.isSuccess())
            throw IOException("LRCLIB answered ${response.status}")

        json.parseToJsonElement(response.bodyAsText()).jsonArray
            .map { it.jsonObject }
            .filter { it.lasts(query.duration) }
            // The most finely timed, the first one LRCLIB ranks among equals
            .mapNotNull { it.toFound() }
            .maxByOrNull { it.sync }
    }

    /** Whether the entry is the same length as the song, give or take what LRCLIB allows. */
    private fun JsonObject.lasts(duration: Duration?): Boolean {
        val entry = this["duration"]?.jsonPrimitive?.doubleOrNull ?: return true
        return duration == null || (entry.seconds - duration).absoluteValue <= DURATION_TOLERANCE
    }

    /** @return `null` for an instrumental or an entry without lyrics */
    private fun JsonObject.toFound(): FoundLyrics? {
        if (this["instrumental"]?.jsonPrimitive?.booleanOrNull == true) return null

        val synced = string("syncedLyrics")
        if (synced != null) {
            val sync = if (WORD_TIME.containsMatchIn(synced)) LyricsSync.WORD else LyricsSync.LINE
            return FoundLyrics(synced, sync, id)
        }
        return string("plainLyrics")?.let { FoundLyrics(it, LyricsSync.NONE, id) }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        const val ID = "lrclib"

        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "Kreate"
        private val DURATION_TOLERANCE = 2.seconds

        /** The time of a word in enhanced LRC, like `<01:02.34>`. */
        private val WORD_TIME = Regex("""<\d+:\d+(\.\d+)?>""")
    }
}
