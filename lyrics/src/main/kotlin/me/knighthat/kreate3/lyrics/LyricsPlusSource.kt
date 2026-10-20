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
import io.ktor.client.plugins.retry
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate3.database.model.LyricsSync
import java.io.IOException

/**
 * LyricsPlus, the server of the YouLy+ extension, run on a few mirrors by its community. It
 * gathers lyrics from Apple Music, Musixmatch, QQ Music and others, mostly timed per word, and
 * answers with its own JSON.
 *
 * The mirrors come and go, so each is asked in turn until one answers.
 *
 * @param mirrors addresses of the servers, in the order they are asked
 */
class LyricsPlusSource(
    private val client: HttpClient,
    private val json: Json,
    private val mirrors: List<String> = MIRRORS,
) : LyricsSource {

    override val id: String = ID
    override val name: String = "LyricsPlus"

    /** A mirror that found nothing ends the search only when no other mirror answers. */
    override suspend fun find(query: LyricsQuery): FoundLyrics? = withContext(Dispatchers.Default) {
        var notFound = false
        var failure: Exception? = null

        for (mirror in mirrors) {
            try {
                val response = client.get("$mirror/v2/lyrics/get") {
                    // The next mirror is the retry
                    retry { noRetry() }
                    parameter("title", query.title)
                    query.artists.firstOrNull()?.let { parameter("artist", it) }
                    query.duration?.let { parameter("duration", it.inWholeSeconds) }
                    query.album?.let { parameter("album", it) }
                }
                if (response.status == HttpStatusCode.NotFound) {
                    notFound = true
                    continue
                }
                if (!response.status.isSuccess())
                    throw IOException("LyricsPlus at $mirror answered ${response.status}")

                return@withContext read(response.bodyAsText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure?.addSuppressed(e) ?: run { failure = e }
            }
        }

        if (notFound) null else throw failure ?: IOException("No LyricsPlus mirror to ask")
    }

    /** @return `null` when the answer has no lines */
    private fun read(body: String): FoundLyrics? {
        val answer = json.parseToJsonElement(body).jsonObject
        if ((answer["lyrics"] as? JsonArray).isNullOrEmpty()) return null

        val sync = when (answer["type"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "word", "syllable" -> LyricsSync.WORD
            "line" -> LyricsSync.LINE
            else -> LyricsSync.NONE
        }
        val credit = answer["metadata"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull
        return FoundLyrics(body, sync, id, credit)
    }

    companion object {
        const val ID = "lyricsplus"

        val MIRRORS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.binimum.org",
        )
    }
}
