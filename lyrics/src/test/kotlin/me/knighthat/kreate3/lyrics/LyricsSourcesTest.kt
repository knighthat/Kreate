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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import me.knighthat.kreate3.database.model.LyricsSync
import me.knighthat.kreate3.database.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class LyricsSourcesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val query = LyricsQuery(
        songId = "s1",
        origin = Origin.LOCAL,
        title = "Song",
        artists = listOf("Artist"),
        duration = 200.seconds,
    )

    /** Every request is answered by [answer], and kept in [requests]. */
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(answer: (HttpRequestData) -> Pair<HttpStatusCode, String>) =
        HttpClient(MockEngine { request ->
            requests += request
            val (status, body) = answer(request)
            respond(body, status)
        })

    private fun lrcLibEntry(
        duration: Double,
        plain: String? = null,
        synced: String? = null,
        instrumental: Boolean = false,
    ) = """{"duration":$duration,"instrumental":$instrumental,""" +
            """"plainLyrics":${JsonPrimitive(plain)},"syncedLyrics":${JsonPrimitive(synced)}}"""

    @Test
    fun lrcLibGivesTheMostFinelyTimedEntryOfTheSameLength() = runBlocking {
        val body = listOf(
            lrcLibEntry(200.0, plain = "plain"),
            lrcLibEntry(260.0, synced = "[00:01.00]<00:01.00>other <00:01.50>version"),
            lrcLibEntry(201.0, plain = "plain", synced = "[00:01.00]line"),
        ).joinToString(",", "[", "]")
        val source = LrcLibSource(client { HttpStatusCode.OK to body }, json)

        val found = source.find(query)!!

        assertEquals("[00:01.00]line", found.text)
        assertEquals(LyricsSync.LINE, found.sync)
        assertEquals("lrclib", found.source)
        assertEquals("Song", requests.single().url.parameters["track_name"])
        assertEquals("Artist", requests.single().url.parameters["artist_name"])
    }

    @Test
    fun lrcLibSeesWordTiming() = runBlocking {
        val body = "[${lrcLibEntry(200.0, synced = "[00:01.00]<00:01.00>la <00:01.50>la")}]"

        val found = LrcLibSource(client { HttpStatusCode.OK to body }, json).find(query)!!

        assertEquals(LyricsSync.WORD, found.sync)
    }

    @Test
    fun lrcLibHasNothingForAnInstrumentalOrNoEntry() = runBlocking {
        val instrumental = "[${lrcLibEntry(200.0, instrumental = true)}]"

        assertNull(LrcLibSource(client { HttpStatusCode.OK to instrumental }, json).find(query))
        assertNull(LrcLibSource(client { HttpStatusCode.OK to "[]" }, json).find(query))
    }

    @Test
    fun lrcLibFailsOnAServerError() {
        val source = LrcLibSource(client { HttpStatusCode.InternalServerError to "" }, json)

        assertThrows(IOException::class.java) { runBlocking { source.find(query) } }
    }

    @Test
    fun betterLyricsReadsTheTimingOfTheTtml() = runBlocking {
        val ttml = """<tt itunes:timing="Word"><body/></tt>"""
        val body = """{"ttml":${JsonPrimitive(ttml)}}"""

        val found = BetterLyricsSource(client { HttpStatusCode.OK to body }, json).find(query)!!

        assertEquals(ttml, found.text)
        assertEquals(LyricsSync.WORD, found.sync)
        assertEquals("betterlyrics", found.source)
        assertEquals("200", requests.single().url.parameters["d"])
    }

    @Test
    fun betterLyricsHasNothingForASongThatNeedsAKey() = runBlocking {
        val body = """{"error":"API key required"}"""

        assertNull(
            BetterLyricsSource(client { HttpStatusCode.Unauthorized to body }, json).find(
                query
            )
        )
        assertNull(BetterLyricsSource(client { HttpStatusCode.NotFound to "" }, json).find(query))
    }

    @Test
    fun betterLyricsFailsOnAServerError() {
        val source = BetterLyricsSource(client { HttpStatusCode.BadGateway to "" }, json)

        assertThrows(IOException::class.java) { runBlocking { source.find(query) } }
    }

    private val lyricsPlusBody =
        """{"type":"Word","metadata":{"source":"Apple"},"lyrics":[{"time":0,"text":"la"}]}"""

    @Test
    fun lyricsPlusMovesOnToTheNextMirror() = runBlocking {
        val source = LyricsPlusSource(
            client { request ->
                if (request.url.host == "down.test") HttpStatusCode.BadGateway to ""
                else HttpStatusCode.OK to lyricsPlusBody
            },
            json,
            listOf("https://down.test", "https://up.test"),
        )

        val found = source.find(query)!!

        assertEquals(lyricsPlusBody, found.text)
        assertEquals(LyricsSync.WORD, found.sync)
        assertEquals("Apple", found.credit)
        assertEquals(2, requests.size)
    }

    @Test
    fun lyricsPlusHasNothingWhenAMirrorFoundNothing() = runBlocking {
        val source = LyricsPlusSource(
            client { request ->
                if (request.url.host == "down.test") HttpStatusCode.BadGateway to ""
                else HttpStatusCode.NotFound to """{"error":{}}"""
            },
            json,
            listOf("https://down.test", "https://empty.test"),
        )

        assertNull(source.find(query))
    }

    @Test
    fun lyricsPlusFailsWhenNoMirrorAnswers() {
        val source = LyricsPlusSource(
            client { HttpStatusCode.BadGateway to "" },
            json,
            listOf("https://a.test", "https://b.test"),
        )

        assertThrows(IOException::class.java) { runBlocking { source.find(query) } }
    }
}
