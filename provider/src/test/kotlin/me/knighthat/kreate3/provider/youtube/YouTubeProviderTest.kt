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

    /**
     * @param region the chosen one, `null` takes the country of [locale]
     * @param cookies of the session, none for nobody signed in
     */
    private fun provider(
        locale: Locale = Locale.US,
        region: String? = null,
        status: HttpStatusCode = HttpStatusCode.OK,
        cookies: String = "",
        statusOf: (body: JsonObject) -> HttpStatusCode = { status },
        respond: (endpoint: String, body: JsonObject) -> String = { _, _ -> fixture("empty") },
    ): YouTubeProvider {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = respond(
                    request.url.encodedPath.substringAfter("/youtubei/v1/"),
                    request.json()
                ),
                status = statusOf(request.json()),
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
        }
        val client = HttpClient(engine)
        return YouTubeProvider(
            InnerTube(
                { client },
                Json { ignoreUnknownKeys = true },
                locale = { locale },
                region = { region ?: locale.youTubeGl },
                cookies = { cookies },
            ),
            YouTubeParser { null },
        )
    }

    private fun HttpRequestData.json(): JsonObject = runBlocking {
        Json.parseToJsonElement(body.toByteArray().decodeToString()).jsonObject
    }

    private fun JsonObject.string(vararg keys: Any): String? = path(*keys).asString

    private val HttpRequestData.endpoint: String
        get() = url.encodedPath.substringAfter("/youtubei/v1/")

    @Test
    fun anAlbumIsSavedAsThePlaylistOfItsSongs() = runBlocking {
        val youTube = provider(cookies = "SAPISID=sap")

        youTube.saveAlbum("OLAK5uy_1", true)
        youTube.saveAlbum("OLAK5uy_1", false)

        assertEquals(listOf("like/like", "like/removelike"), requests.map { it.endpoint })
        assertEquals(
            listOf("OLAK5uy_1", "OLAK5uy_1"),
            requests.map { it.json().string("target", "playlistId") },
        )
    }

    @Test
    fun anArtistIsSubscribedToByTheirChannel() = runBlocking {
        val youTube = provider(cookies = "SAPISID=sap")

        youTube.subscribe("UC_channel", true)
        youTube.subscribe("UC_channel", false)

        assertEquals(
            listOf("subscription/subscribe", "subscription/unsubscribe"),
            requests.map { it.endpoint },
        )
        val body = requests.first().json()
        assertEquals("UC_channel", body.string("channelIds", 0))
        assertEquals("EgIIAhgA", body.string("params"))
    }

    @Test
    fun savingNeedsSomeoneSignedIn() {
        val youTube = provider()

        val saving = assertThrows(YouTubeException::class.java) {
            runBlocking { youTube.saveAlbum("OLAK5uy_1", true) }
        }
        val subscribing = assertThrows(YouTubeException::class.java) {
            runBlocking { youTube.subscribe("UC_channel", true) }
        }

        assertTrue(saving.signedOut)
        assertTrue(subscribing.signedOut)
        assertTrue(requests.isEmpty())
    }

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
    fun theChosenRegionIsSentInPlaceOfTheCountryOfTheLanguage() = runBlocking {
        provider(Locale.CANADA_FRENCH, region = "VN").albumPage("MPREb_BQHrCwSBM37")

        val body = requests.single().json()
        assertEquals("fr-CA", body.string("context", "client", "hl"))
        assertEquals("VN", body.string("context", "client", "gl"))
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

        assertEquals("First line\nSecond line\n", lyrics.text)
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

    private fun JsonObject.videoIds(): List<String?> =
        this["videoIds"].asArray.orEmpty().map { it.asString }

    @Test
    fun lookUpsPairEntriesWithTheIdsAskedByPosition() = runBlocking {
        // An answer with azxk0-7Zjhk unplayable (published again as bM0x19DYsmA), and bM0x19DYsmA
        val provider = provider { endpoint, _ ->
            if (endpoint == "music/get_queue") fixture("queue_unplayable") else fixture("empty")
        }

        val lookups = provider.lookUpSongs(listOf("azxk0-7Zjhk", "bM0x19DYsmA"))!!

        assertEquals(SongLookup.Unplayable, lookups["azxk0-7Zjhk"])
        assertEquals("bM0x19DYsmA", (lookups["bM0x19DYsmA"] as SongLookup.Found).song.song.id)
        assertEquals(1, requests.size)

        // An entry with another id than the one asked in its place doesn't stand for it, even one
        // that plays
        val swapped = provider.lookUpSongs(listOf("bM0x19DYsmA", "azxk0-7Zjhk"))!!
        assertEquals(SongLookup.Unplayable, swapped["bM0x19DYsmA"])
    }

    private val visitorAnswer = """{"responseContext":{"visitorData":"VISITOR"}}"""

    @Test
    fun aRefusedLookUpIsAskedOnceMoreWithAVisitorId() = runBlocking {
        val provider = provider(
            statusOf = { body -> if ("videoIds" in body) HttpStatusCode.Forbidden else HttpStatusCode.OK },
        ) { endpoint, _ -> if (endpoint == "visitor_id") visitorAnswer else fixture("empty") }

        assertNull(provider.lookUpSongs(listOf("private", "a", "b")))

        assertEquals(
            listOf("music/get_queue", "visitor_id", "music/get_queue"),
            requests.map { it.endpoint })
        assertNull(requests[0].json().string("context", "client", "visitorData"))
        assertEquals("VISITOR", requests[2].json().string("context", "client", "visitorData"))
        // Never split
        assertEquals(listOf("private", "a", "b"), requests[2].json().videoIds())
    }

    @Test
    fun aLookUpRefusedWithAVisitorIdIsNotAskedAgain() = runBlocking {
        val provider = provider(
            statusOf = { body ->
                if ("private" in body.videoIds()) HttpStatusCode.Forbidden else HttpStatusCode.OK
            },
        ) { endpoint, _ -> if (endpoint == "visitor_id") visitorAnswer else fixture("empty") }
        provider.lookUpSongs(listOf("x"))
        provider.lookUpSongs(listOf("private"))
        requests.clear()

        assertNull(provider.lookUpSongs(listOf("private")))
        assertEquals(1, requests.size)
    }

    @Test
    fun lookUpsGoInBatchesOfAThousandAndOneRefusedBatchRefusesAll() = runBlocking {
        val ids = (1..2_500).map { "id$it" }
        val provider = provider(
            statusOf = { body ->
                if ("id1500" in body.videoIds()) HttpStatusCode.Forbidden else HttpStatusCode.OK
            },
        ) { endpoint, _ -> if (endpoint == "visitor_id") visitorAnswer else fixture("empty") }

        assertNull(provider.lookUpSongs(ids))

        // The first answer gave a visitor id, so the refused second batch isn't asked again, and
        // the third is never sent
        assertEquals(listOf("music/get_queue", "music/get_queue"), requests.map { it.endpoint })
        assertEquals(listOf(1_000, 1_000), requests.map { it.json().videoIds().size })
        assertEquals("id1001", requests[1].json().videoIds().first())
        assertEquals(
            listOf(null, "CgtWc3ZkODhTa2o2VSjS9rvWBjIKCgJVUxIEGgAgYw%3D%3D"),
            requests.map { it.json().string("context", "client", "visitorData") },
        )
    }

    @Test
    fun aLookUpThatCanNotReachYouTubeThrows() {
        val engine = MockEngine { throw java.io.IOException("offline") }
        val client = HttpClient(engine)
        val provider = YouTubeProvider(
            InnerTube({ client }, Json { ignoreUnknownKeys = true }, locale = { Locale.US }),
            YouTubeParser { null },
        )

        assertThrows(YouTubeException::class.java) {
            runBlocking { provider.lookUpSongs(listOf("a")) }
        }
    }
}
