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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Locale

class YouTubeProviderTest {

    // Searches send their requests in parallel
    private val requests: MutableList<HttpRequestData> =
        Collections.synchronizedList(mutableListOf())

    private fun fixture(name: String): String =
        javaClass.getResource("/youtube/$name.json")!!.readText()

    private fun provider(
        locale: Locale = Locale.US,
        status: HttpStatusCode = HttpStatusCode.OK,
        respond: (endpoint: String, body: JsonObject) -> String = { _, _ -> fixture("empty") },
    ): YouTubeProvider {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = respond(
                    request.url.encodedPath.substringAfter("/youtubei/v1/"),
                    request.json()
                ),
                status = status,
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
        }
        return YouTubeProvider(
            InnerTube(HttpClient(engine), Json { ignoreUnknownKeys = true }, locale = { locale }),
            YouTubeParser { null },
        )
    }

    private fun HttpRequestData.json(): JsonObject = runBlocking {
        Json.parseToJsonElement(body.toByteArray().decodeToString()).jsonObject
    }

    private fun JsonObject.string(vararg keys: Any): String? = path(*keys).asString

    @Test
    fun requestsAreSentAsTheWebsiteInTheLanguageOfTheDevice() = runBlocking {
        provider(Locale.CANADA_FRENCH).albumPage("MPREb_BQHrCwSBM37")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(
            "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",
            request.url.toString(),
        )
        val body = request.json()
        assertEquals("WEB_REMIX", body.string("context", "client", "clientName"))
        assertEquals("fr-CA", body.string("context", "client", "hl"))
        assertEquals("CA", body.string("context", "client", "gl"))
        assertEquals("MPREb_BQHrCwSBM37", body.string("browseId"))
        assertNull(body.string("context", "client", "visitorData"))
    }

    @Test
    fun theVisitorOfTheFirstAnswerIsSentAfterwards() = runBlocking {
        val provider = provider()
        provider.album("MPREb_a")
        provider.album("MPREb_b")

        val visitor = Json.parseToJsonElement(fixture("empty"))
            .path("responseContext", "visitorData").asString!!
        val second = requests[1]
        assertEquals(visitor, second.json().string("context", "client", "visitorData"))
        assertEquals(visitor, second.headers["X-Goog-Visitor-Id"])
    }

    private val searches: (String, JsonObject) -> String = { _, body ->
        when {
            body.string("continuation") != null -> fixture("search_cont")
            body.string("params") == YouTubeSearchFilter.SONGS.params -> fixture("search_songs")
            body.string("params") == YouTubeSearchFilter.ALBUMS.params -> fixture("search_albums")
            body.string("params") == YouTubeSearchFilter.ARTISTS.params -> fixture("search_artists")
            else -> fixture("empty")
        }
    }

    @Test
    fun searchAsksEachKindOnceWhenTheFirstPageIsEnough() = runBlocking {
        val results = provider(respond = searches).search("daft punk", limit = 3)

        assertEquals(3, requests.size)
        assertEquals(
            setOf(
                YouTubeSearchFilter.SONGS,
                YouTubeSearchFilter.ALBUMS,
                YouTubeSearchFilter.ARTISTS
            )
                .map { it.params }.toSet(),
            requests.map { it.json().string("params") }.toSet(),
        )
        assertTrue(requests.all { it.json().string("query") == "daft punk" })
        assertEquals("a7Wask21bew", results.songs.first().id)
        assertEquals(3, results.songs.size)
        assertEquals("MPREb_K8qWMWVqXGi", results.albums.first().id)
        assertEquals("UCRr1xG_2WIDs18a6cIiCxeA", results.artists.first().id)
    }

    @Test
    fun searchAsksForMoreUntilTheWindowIsFilled() = runBlocking {
        val provider = provider(respond = searches)
        val first = provider.search("daft punk", YouTubeSearchFilter.SONGS).items
        val second = provider.search("daft punk", YouTubeSearchFilter.SONGS, "token").items
        requests.clear()

        val results = provider.search("daft punk", limit = 4, offset = 4)

        val ids = (first + second).map { (it as YouTubeItem.SongItem).song.song.id }
        assertEquals(ids.subList(4, 8), results.songs.map { it.id })
        assertTrue(requests.any { it.json().string("continuation") != null })
    }

    @Test
    fun blankSearchSendsNothing() = runBlocking {
        val provider = provider()

        assertTrue(provider.search(" ").songs.isEmpty())
        assertTrue(provider.suggestions("").queries.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun unknownItemsAreNull() = runBlocking {
        val provider = provider()

        assertNull(provider.album("MPREb_x"))
        assertNull(provider.artist("UCx"))
        assertNull(provider.song("x"))
        assertNull(provider.playlistPage("PLx"))
        assertTrue(provider.songsOf("MPREb_x").isEmpty())
        assertTrue(provider.albumsOf("UCx").isEmpty())
    }

    @Test
    fun aFailedAnswerThrows() {
        val provider = provider(status = HttpStatusCode.TooManyRequests)

        val error = assertThrows(YouTubeException::class.java) {
            runBlocking { provider.album("MPREb_x") }
        }
        assertEquals(429, error.status)
    }

    @Test
    fun anAnswerThatIsNotJsonThrows() {
        val provider = provider { _, _ -> "<html></html>" }

        assertThrows(YouTubeException::class.java) {
            runBlocking { provider.album("MPREb_x") }
        }
    }

    @Test
    fun albumsOfOpensTheDiscography() = runBlocking {
        val provider = provider { _, body ->
            when (body.string("browseId")) {
                "UCRr1xG_2WIDs18a6cIiCxeA" -> fixture("artist")
                "MPADUCRr1xG_2WIDs18a6cIiCxeA" -> fixture("disco")
                else -> fixture("empty")
            }
        }

        val albums = provider.albumsOf("UCRr1xG_2WIDs18a6cIiCxeA")

        val discography =
            requests.single { it.json().string("browseId") == "MPADUCRr1xG_2WIDs18a6cIiCxeA" }
        assertEquals("ggMIegYIAhoCAQI%3D", discography.json().string("params"))
        val ids = albums.map { it.id }
        assertTrue("MPREb_OgeEnoHTsCm" in ids)
        assertTrue("MPREb_X1DQ1j0PPrX" in ids)
        assertEquals(ids.distinct(), ids)
        assertTrue(albums.all { it.artists == listOf("Daft Punk") })
    }

    @Test
    fun songDetailsComeFromTheQueue() = runBlocking {
        val provider =
            provider { endpoint, _ -> if (endpoint == "next") fixture("next") else fixture("empty") }

        val song = provider.songDetails("khnokW3Mw24")!!

        assertEquals("Instant Crush (feat. Julian Casablancas)", song.song.name)
        assertEquals("khnokW3Mw24", requests.single().json().string("videoId"))
        assertNull(provider.songDetails("not-in-the-queue"))
    }

    @Test
    fun lyricsAreReadFromTheirTab() = runBlocking {
        val provider = provider { endpoint, body ->
            when {
                endpoint == "next" -> fixture("next")
                body.string("browseId") == "MPLYt_K8qWMWVqXGi-5" -> fixture("lyrics")
                else -> fixture("empty")
            }
        }

        val lyrics = provider.lyrics("khnokW3Mw24")!!

        assertEquals("First line\nSecond line\n", lyrics.lyrics.lyrics)
        assertEquals("khnokW3Mw24", lyrics.lyrics.songId)
    }

    @Test
    fun relatedAreReadFromTheirTab() = runBlocking {
        val provider = provider { endpoint, body ->
            when {
                endpoint == "next" -> fixture("next")
                body.string("browseId") == "MPTRt_K8qWMWVqXGi-5" -> fixture("related")
                else -> fixture("empty")
            }
        }

        assertEquals(5, provider.related("khnokW3Mw24").size)
    }

    @Test
    fun allPlaylistSongsFollowsTheContinuations() = runBlocking {
        val provider = provider { _, body ->
            if (body.string("continuation") != null) fixture("pl_cont") else fixture("pl_top")
        }

        val songs = provider.allPlaylistSongs("OLAK5uy_lNVBcjNtiCwq-n95uoxJ-Gd4vsfMkyZNs")!!

        assertEquals(11, songs.size)
        assertEquals(
            "VLOLAK5uy_lNVBcjNtiCwq-n95uoxJ-Gd4vsfMkyZNs",
            requests.first().json().string("browseId")
        )
        assertEquals(2, requests.size)
    }

    @Test
    fun radioPlaysFromTheSongAndGoesOn() = runBlocking {
        val provider = provider { _, body ->
            if (body.string("continuation") != null) fixture("radio_cont") else fixture("radio")
        }

        val first = provider.radio("a7Wask21bew")
        provider.radio("a7Wask21bew", first.continuation)

        assertEquals("a7Wask21bew", first.items.first().song.song.id)
        val (start, next) = requests.map { it.json() }
        assertEquals("RDAMVMa7Wask21bew", start.string("playlistId"))
        assertEquals("a7Wask21bew", start.string("videoId"))
        assertEquals(first.continuation, next.string("continuation"))
        assertEquals("RDAMVMa7Wask21bew", next.string("playlistId"))
    }

    @Test
    fun homeContinuationIsAskedWithTheToken() = runBlocking {
        val provider = provider { _, body ->
            if (body.string("continuation") != null) fixture("home_cont") else fixture("home")
        }

        val home = provider.home()
        val more = provider.home(home.continuation)

        assertEquals("FEmusic_home", requests[0].json().string("browseId"))
        assertEquals(home.continuation, requests[1].json().string("continuation"))
        assertEquals(3, more.items.size)
    }

    @Test
    fun playbackIsNotFetched() = runBlocking {
        assertNull(provider().playbackUri("a7Wask21bew"))
        assertTrue(requests.isEmpty())
    }
}
