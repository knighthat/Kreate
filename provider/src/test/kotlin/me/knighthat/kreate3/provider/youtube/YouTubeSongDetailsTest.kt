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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Collections
import java.util.Locale

class YouTubeSongDetailsTest {

    /** The ids of every request. */
    private val requests: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())

    /**
     * Answers like YouTube: the songs of the ids asked, or 404 when one of them is in [unknown],
     * or [failure] for every request.
     */
    private fun provider(
        unknown: Set<String> = emptySet(),
        failure: HttpStatusCode? = null,
        unknownStatus: HttpStatusCode = HttpStatusCode.NotFound,
    ): YouTubeProvider {
        val engine = MockEngine { request ->
            val body =
                Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            val ids = body.getValue("videoIds").jsonArray.map { it.jsonPrimitive.content }
            requests += ids

            val status = failure ?: unknownStatus.takeIf { ids.any { it in unknown } }
            ?: HttpStatusCode.OK
            val content = if (status == HttpStatusCode.OK)
                """{"queueDatas":[${ids.joinToString(",") { panel(it) }}]}"""
            else
                """{"error":{"code":${status.value}}}"""
            respond(
                content = content,
                status = status,
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
        }
        val client = HttpClient(engine)
        return YouTubeProvider(
            InnerTube(
                { client },
                Json { ignoreUnknownKeys = true },
                locale = { Locale.US },
                region = { "US" },
                cookies = { "" },
            ),
            YouTubeParser { null },
        )
    }

    private fun panel(id: String) = """
        {"content":{"playlistPanelVideoRenderer":{
          "videoId":"$id",
          "title":{"runs":[{"text":"Song $id"}]},
          "longBylineText":{"runs":[{"text":"Artist"}]},
          "lengthText":{"runs":[{"text":"3:34"}]},
          "thumbnail":{"thumbnails":[{"url":"https://example.invalid/$id","width":60,"height":60}]}
        }}}
    """.trimIndent()

    private fun ids(count: Int) = List(count) { "id$it" }

    @Test
    fun asksOnceForAllTheSongs() = runBlocking {
        val songs = provider().songDetailsOf(ids(100))

        assertEquals(ids(100), songs.map { it.song.id })
        assertEquals("Song id7", songs[7].song.name)
        assertEquals(1, requests.size)
    }

    @Test
    fun leavesOutWhatYouTubeDoesNotKnow() = runBlocking {
        val songs = provider(unknown = setOf("id3", "id70")).songDetailsOf(ids(100))

        assertEquals(ids(100) - setOf("id3", "id70"), songs.map { it.song.id })
        // Far fewer than a request for every song
        assertEquals(true, requests.size < 30)
    }

    @Test
    fun anUnknownIdMayAlsoAnswerWithAnInternalError() = runBlocking {
        val songs =
            provider(unknown = setOf("id3"), unknownStatus = HttpStatusCode.InternalServerError)
                .songDetailsOf(ids(10))

        assertEquals(ids(10) - "id3", songs.map { it.song.id })
    }

    @Test
    fun anAnswerOtherThanNotFoundIsAnError() {
        val youTube = provider(failure = HttpStatusCode.BadGateway)

        assertThrows(YouTubeException::class.java) { runBlocking { youTube.songDetailsOf(ids(5)) } }
        assertEquals(1, requests.size)
    }

    @Test
    fun asksForNothingWhenThereIsNothing() = runBlocking {
        assertEquals(emptyList<Any>(), provider().songDetailsOf(emptyList()))
        assertEquals(0, requests.size)
    }
}
