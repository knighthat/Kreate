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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Requests in the session of a signed in user, and their library. The answers are the smallest
 * YouTube would give.
 */
class YouTubeSignInTest {

    private val requests: MutableList<HttpRequestData> =
        Collections.synchronizedList(mutableListOf())

    private var cookies = SESSION

    private var pageId = ""

    private fun provider(
        answer: (endpoint: String, body: JsonObject) -> String = { _, _ ->
            signedIn(
                "{}"
            )
        }
    ): YouTubeProvider {
        val client = HttpClient(MockEngine { request ->
                    requests += request
                    respond(
                        content = answer(
                            request.url.encodedPath.substringAfter("/youtubei/v1/"),
                            request.json()
                        ),
                        headers = Headers.build {
                            append(
                                HttpHeaders.ContentType,
                                "application/json"
                            )
                        },
                    )
        })
        return YouTubeProvider(
            InnerTube(
                client = { client },
                json = Json,
                locale = { Locale.US },
                cookies = { cookies },
                clock = object : Clock {
                    override fun now() = Instant.fromEpochSeconds(1_700_000_000)
                },
                pageId = { pageId },
            ),
            YouTubeParser { null },
        )
    }

    private fun HttpRequestData.json(): JsonObject = runBlocking {
        Json.parseToJsonElement(body.toByteArray().decodeToString()).jsonObject
    }

    @Test
    fun requestsAreSignedLikeTheWebsiteSignsThem() = runBlocking {
        provider().album("MPREb_1")

        val headers = requests.single().headers
        assertEquals(SESSION, headers[HttpHeaders.Cookie])
        assertEquals(
            "SAPISIDHASH 1700000000_9a820ff9b7c4f184eef2132ec4d5cef13d503972 " +
                    "SAPISID1PHASH 1700000000_2925ad1b3a13edf42429c355dd2c3a5e6d37eda5 " +
                    "SAPISID3PHASH 1700000000_b0b4c6f0b13f54bfa874a3b6552191b7573d1672",
            headers[HttpHeaders.Authorization],
        )
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("https://music.youtube.com", headers["X-Origin"])
    }

    @Test
    fun withoutASessionNothingIsSigned() = runBlocking {
        // Cookies of a visit without signing in have no session cookie
        cookies = "PREF=f6=40000000; YSC=abc"
        val provider = provider()

        provider.album("MPREb_1")

        val headers = requests.single().headers
        assertNull(headers[HttpHeaders.Cookie])
        assertNull(headers[HttpHeaders.Authorization])
        assertFalse(provider.isSignedIn())
        assertNull(provider.sessionId())
    }

    @Test
    fun theOnlySessionCookieSignsAlone() {
        val session = YouTubeSession.of("__Secure-3PAPISID=p3")!!

        assertEquals("p3", session.sapisid)
        assertEquals(
            "SAPISIDHASH 1700000000_b0b4c6f0b13f54bfa874a3b6552191b7573d1672 " +
                    "SAPISID3PHASH 1700000000_b0b4c6f0b13f54bfa874a3b6552191b7573d1672",
            session.authorization(1_700_000_000, "https://music.youtube.com"),
        )
        assertNull(YouTubeSession.of(""))
        assertTrue(isYouTubeSession(SESSION))
        assertFalse(isYouTubeSession("YSC=abc"))
    }

    @Test
    fun aVisitorIdIsNotCarriedToAnotherSession() = runBlocking {
        val provider = provider { _, _ -> """{"responseContext":{"visitorData":"V1"}}""" }
        provider.album("MPREb_1")
        provider.album("MPREb_1")
        cookies = "SAPISID=other"
        provider.album("MPREb_1")

        val sent = requests.map { it.json().path("context", "client", "visitorData").asString }
        assertEquals(listOf(null, "V1", null), sent)
    }

    @Test
    fun nobodySignedInHasNoLibraryAndNothingIsAsked() = runBlocking {
        cookies = ""
        val provider = provider()

        assertNull(provider.libraryAlbums())
        assertNull(provider.libraryArtists())
        assertNull(provider.libraryPlaylists())
        assertNull(provider.account())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun libraryAlbumsComePartAfterPart() = runBlocking {
        val provider = provider { _, body ->
            if (body.path("continuation") != null) signedIn(ALBUMS_MORE) else signedIn(ALBUMS)
        }

        val albums = provider.libraryAlbums()!!

        assertEquals(listOf("MPREb_a1", "MPREb_a2", "MPREb_a3"), albums.map { it.id })
        assertEquals(listOf("Artist"), albums.first().artists)
        assertEquals(2001, albums.first().year)
        assertEquals("FEmusic_liked_albums", requests.first().json().path("browseId").asString)
        assertEquals("T", requests[1].json().path("continuation").asString)
    }

    @Test
    fun libraryArtistsAreTheSubscriptions() = runBlocking {
        val artists = provider { _, _ -> signedIn(ARTISTS) }.libraryArtists()!!

        assertEquals(listOf("UC1", "UC2"), artists.map { it.id })
        assertEquals(
            "FEmusic_library_corpus_artists",
            requests.single().json().path("browseId").asString,
        )
    }

    @Test
    fun libraryPlaylistsSkipTheNewPlaylistButton() = runBlocking {
        val playlists = provider { _, _ -> signedIn(PLAYLISTS) }.libraryPlaylists()!!

        assertEquals(listOf("LM", "PL1"), playlists.map { it.remoteId })
        assertEquals("Liked Music", playlists.first().name)
    }

    private fun fixture(name: String): String =
        javaClass.getResource("/youtube/$name.json")!!.readText()

    @Test
    fun anEmptyLibraryAsYouTubeAnswersItListsNothing() = runBlocking {
        // A message stands where the grid would be, "No albums yet"
        val provider = provider { _, _ -> fixture("library_empty") }

        assertEquals(emptyList<Any>(), provider.libraryAlbums())
        assertEquals(emptyList<Any>(), provider.libraryArtists())
    }

    @Test
    fun libraryPlaylistsAsYouTubeAnswersThem() = runBlocking {
        val playlists = provider { _, _ -> fixture("library_playlists") }.libraryPlaylists()!!

        assertEquals(listOf("SE"), playlists.map { it.remoteId })
        assertEquals("Episodes for Later", playlists.single().name)
    }

    @Test
    fun channelsAreReadAsYouTubeListsThem() = runBlocking {
        val channels = provider { _, _ -> fixture("accounts_list") }.channels()!!

        assertEquals(listOf("Own Channel", "Brand Channel"), channels.map { it.name })
        assertEquals(listOf("@own", "@brand"), channels.map { it.handle })
        // The account's own channel is acted as without a page id
        assertEquals(listOf(null, "1111"), channels.map { it.pageId })
        assertEquals(listOf(true, false), channels.map { it.isSelected })
        assertTrue(requests.single().url.encodedPath.endsWith("/account/accounts_list"))
    }

    @Test
    fun nobodySignedInHasNoChannels() = runBlocking {
        cookies = ""

        assertNull(provider().channels())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aChosenChannelIsActedAs() = runBlocking {
        pageId = "1111"

        provider().album("MPREb_1")

        val request = requests.single()
        assertEquals("1111", request.json().path("context", "user", "onBehalfOfUser").asString)
        assertEquals("1111", request.headers["X-Goog-PageId"])
    }

    @Test
    fun theAccountsOwnChannelSendsNoPageId() = runBlocking {
        provider().album("MPREb_1")

        val request = requests.single()
        assertNull(request.json().path("context", "user"))
        assertNull(request.headers["X-Goog-PageId"])
    }

    @Test
    fun anotherChannelIsAnotherSession() = runBlocking {
        val provider = provider { _, _ -> """{"responseContext":{"visitorData":"V1"}}""" }
        val own = provider.sessionId()
        provider.album("MPREb_1")
        pageId = "1111"
        provider.album("MPREb_1")

        assertTrue(own != provider.sessionId())
        // The visitor of one channel isn't the other's
        assertNull(requests[1].json().path("context", "client", "visitorData"))
    }

    @Test
    fun aLibraryAnsweredOutsideTheSessionFails() {
        // An ended session gets the page of somebody signed out, which would list nothing
        val provider = provider { _, _ -> signedOut(ALBUMS) }

        val error = assertThrows(YouTubeException::class.java) {
            runBlocking { provider.libraryAlbums() }
        }
        assertTrue(error.signedOut)
    }

    @Test
    fun otherPagesStillWorkOutsideTheSession() = runBlocking {
        assertNull(provider { _, _ -> signedOut("{}") }.album("MPREb_1"))
    }

    @Test
    fun theAccountIsReadFromItsMenu() = runBlocking {
        val provider = provider { endpoint, _ ->
            if (endpoint == "account/account_menu") signedIn(ACCOUNT) else signedIn("{}")
        }

        val account = provider.account()!!

        assertEquals("Someone", account.name)
        assertEquals("@someone", account.handle)
    }

    @Test
    fun anAccountMenuWithoutAccountMeansTheSessionEnded() {
        val provider = provider { _, _ -> signedIn("{}") }

        val error = assertThrows(YouTubeException::class.java) {
            runBlocking { provider.account() }
        }
        assertTrue(error.signedOut)
    }

    private companion object {

        const val SESSION =
            "SID=s; HSID=h; SAPISID=sap; __Secure-1PAPISID=p1; __Secure-3PAPISID=p3; LOGIN_INFO=l"

        fun context(loggedIn: String) =
            """"responseContext":{"serviceTrackingParams":[{"service":"GFEEDBACK","params":[{"key":"logged_in","value":"$loggedIn"}]}]}"""

        /** [answer] as YouTube gives it in the session. */
        fun signedIn(answer: String) =
            answer.replaceFirst("{", "{${context("1")},").replace(",}", "}")

        fun signedOut(answer: String) =
            answer.replaceFirst("{", "{${context("0")},").replace(",}", "}")

        fun album(id: String) =
            """{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Album $id"}]},
            "subtitle":{"runs":[{"text":"Album"},{"text":" • "},
            {"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UC1",
            "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
            {"text":" • "},{"text":"2001"}]},
            "navigationEndpoint":{"browseEndpoint":{"browseId":"$id",
            "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}}"""

        fun artist(id: String) = """{"musicResponsiveListItemRenderer":{"flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist $id"}]}}}],
            "navigationEndpoint":{"browseEndpoint":{"browseId":"$id",
            "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}}}"""

        fun playlist(browseId: String, name: String) =
            """{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"$name"}]},
            "navigationEndpoint":{"browseEndpoint":{"browseId":"$browseId",
            "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}}}"""

        /** A library page: a shelf in an item section, like YouTube lays them out. */
        fun library(shelf: String) =
            """{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":
            {"content":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[$shelf]}}]}}}}]}}}"""

        val ALBUMS = library(
            """{"gridRenderer":{"items":[${album("MPREb_a1")},${album("MPREb_a2")}],
            "continuations":[{"nextContinuationData":{"continuation":"T"}}]}}"""
        )

        val ALBUMS_MORE =
            """{"continuationContents":{"gridContinuation":{"items":[${album("MPREb_a3")}]}}}"""

        val ARTISTS =
            library("""{"musicShelfRenderer":{"contents":[${artist("UC1")},${artist("UC2")}]}}""")

        val PLAYLISTS = library(
            """{"gridRenderer":{"items":[
            {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"New playlist"}]},
            "navigationEndpoint":{"createPlaylistEndpoint":{}}}},
            ${playlist("VLLM", "Liked Music")},${playlist("VLPL1", "Mine")}]}}"""
        )

        val ACCOUNT = """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":
            {"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Someone"}]},
            "channelHandle":{"runs":[{"text":"@someone"}]},
            "accountPhoto":{"thumbnails":[{"url":"https://yt3.ggpht.com/p=s88","width":88,"height":88}]}}}}}}}]}"""
    }
}
