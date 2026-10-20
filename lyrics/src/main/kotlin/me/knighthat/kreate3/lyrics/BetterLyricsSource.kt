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
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate3.database.model.LyricsSync
import java.io.IOException

/**
 * The server of the [Better Lyrics](https://github.com/boidushya/better-lyrics) extension for
 * YouTube Music: Apple Music's lyrics as TTML, mostly timed per word.
 *
 * It answers without a key only for songs someone already looked up, the rest need a key Kreate
 * doesn't have; to the user those are songs it has no lyrics for.
 */
class BetterLyricsSource(private val client: HttpClient, private val json: Json) : LyricsSource {

    override val id: String = ID
    override val name: String = "Better Lyrics"

    override suspend fun find(query: LyricsQuery): FoundLyrics? = withContext(Dispatchers.Default) {
        val response = client.get("$BASE_URL/getLyrics") {
            parameter("s", query.title)
            query.artists.firstOrNull()?.let { parameter("a", it) }
            query.duration?.let { parameter("d", it.inWholeSeconds) }
            query.album?.let { parameter("al", it) }
        }
        when {
            response.status == HttpStatusCode.NotFound -> return@withContext null
            // A song nobody looked up yet, it needs a key
            response.status == HttpStatusCode.Unauthorized -> return@withContext null
            !response.status.isSuccess() -> throw IOException("Better Lyrics answered ${response.status}")
        }

        val ttml = json.parseToJsonElement(response.bodyAsText()).jsonObject["ttml"]
            ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withContext null
        FoundLyrics(ttml, ttml.sync(), id)
    }

    /** From the `itunes:timing` of the TTML, timed per line when it doesn't say. */
    private fun String.sync(): LyricsSync =
        when (TIMING.find(this)?.groupValues?.get(1)?.lowercase()) {
            "word" -> LyricsSync.WORD
            "none" -> LyricsSync.NONE
            else -> LyricsSync.LINE
        }

    companion object {
        const val ID = "betterlyrics"

        private const val BASE_URL = "https://lyrics-api.boidu.dev"
        private val TIMING = Regex("""itunes:timing="(\w+)"""")
    }
}
