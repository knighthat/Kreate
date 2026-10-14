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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.provider.LibrarySection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class YouTubeLibraryTest {

    /** Remembers what it was asked to store. */
    private class Recorder : RemoteCatalogRepository {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val albums: MutableList<RemoteItem<Album>> = Collections.synchronizedList(mutableListOf())
        val songs: MutableList<RemoteSong> = Collections.synchronizedList(mutableListOf())

        override suspend fun syncAlbums(origin: Origin, albums: List<RemoteItem<Album>>) {
            calls += "$origin albums ${albums.map { it.item.id }}"
            this.albums += albums
        }

        override suspend fun syncArtists(origin: Origin, artists: List<RemoteItem<Artist>>) {
            calls += "$origin artists ${artists.map { it.item.id }}"
        }

        override suspend fun syncPlaylists(origin: Origin, playlists: List<UserPlaylist>) {
            calls += "$origin playlists ${playlists.map { it.remoteId }}"
        }

        override suspend fun syncSongs(
            origin: Origin,
            songs: List<RemoteSong>,
            albums: List<RemoteItem<Album>>,
            artists: List<RemoteItem<Artist>>,
            playlistId: Long?,
        ) {
            calls += "$origin songs ${songs.map { it.song.id }}"
            this.songs += songs
        }

        override suspend fun syncLikedSongs(origin: Origin, songs: List<RemoteSong>) {
            calls += "$origin liked ${songs.map { it.song.id }}"
            this.songs += songs
        }
    }

    private class TestClock(var time: Instant = Instant.fromEpochSeconds(1_000)) : Clock {
        override fun now() = time
    }

    private val repository = Recorder()
    private val clock = TestClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = AtomicInteger()

    @Volatile
    private var cookies = "SAPISID=sap"

    @Volatile
    private var loggedIn = "1"

    @Volatile
    private var pageId = ""

    /** Sections whose syncing is on. */
    private val syncing: MutableSet<LibrarySection> =
        Collections.synchronizedSet(LibrarySection.entries.toMutableSet())

    /** Whether "Liked Music" has more parts than are fetched. */
    @Volatile
    private var endlessLikes = false

    /** Runs when a request arrives, before it is answered. */
    @Volatile
    private var onRequest: () -> Unit = {}

    private val provider = YouTubeProvider(
        InnerTube(
            client = HttpClient(MockEngine { request ->
                requests.incrementAndGet()
                onRequest()
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString())
                respond(
                    content = if (body.path("continuation") != null) moreLikes()
                    else answer(body.path("browseId").asString),
                    headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
                )
            }),
            json = Json,
            locale = { Locale.US },
            cookies = { cookies },
            clock = clock,
            pageId = { pageId },
        ),
        YouTubeParser { null },
    )

    private val library = YouTubeLibrary(provider, repository, scope, clock) { it in syncing }

    private fun answer(browseId: String?): String {
        if (browseId == "VLLM") return likes()
        val shelf = when (browseId) {
            "FEmusic_liked_albums" -> """{"gridRenderer":{"items":[${
                card(
                    "MPREb_a1",
                    "ALBUM"
                )
            },${card("MPREb_a2", "ALBUM")}]}}"""

            "FEmusic_library_corpus_artists" -> """{"gridRenderer":{"items":[${
                card(
                    "UC1",
                    "ARTIST"
                )
            }]}}"""

            "FEmusic_liked_playlists" -> """{"gridRenderer":{"items":[${
                card(
                    "VLLM",
                    "PLAYLIST"
                )
            }]}}"""

            else -> error("Unexpected $browseId")
        }
        return """{"responseContext":{"serviceTrackingParams":[{"params":[{"key":"logged_in","value":"$loggedIn"}]}]},
            "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[$shelf]}}]}}}}]}}}"""
    }

    private val signedIn
        get() = """{"serviceTrackingParams":[{"params":[{"key":"logged_in","value":"$loggedIn"}]}]}"""

    private fun likes(): String {
        val more = if (endlessLikes) ",$continuationItem" else ""
        return """{"responseContext":$signedIn,"contents":{"twoColumnBrowseResultsRenderer":{
            "tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"musicResponsiveHeaderRenderer":{"title":{"runs":[{"text":"Liked Music"}]}}}]}}}}],
            "secondaryContents":{"sectionListRenderer":{"contents":[{"musicPlaylistShelfRenderer":
            {"contents":[${row("s1")},${row("s2")}$more]}}]}}}}}"""
    }

    /** A next part of "Liked Music" that is never the last. */
    private fun moreLikes() = """{"responseContext":$signedIn,"onResponseReceivedActions":[
        {"appendContinuationItemsAction":{"continuationItems":[${row("s3")},$continuationItem]}}]}"""

    private val continuationItem = """{"continuationItemRenderer":{"continuationEndpoint":
        {"continuationCommand":{"token":"more"}}}}"""

    private fun row(id: String) = """{"musicResponsiveListItemRenderer":{"flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$id"}]}}}],
        "playlistItemData":{"videoId":"$id"}}}"""

    private fun card(id: String, type: String) =
        """{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"$id"}]},
        "navigationEndpoint":{"browseEndpoint":{"browseId":"$id",
        "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_$type"}}}}}}"""

    private suspend fun awaitCalls(count: Int) = withTimeout(5_000) {
        while (repository.calls.size < count) delay(10)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun eachSectionIsStoredAsYouTubes() = runBlocking {
        library.refresh(LibrarySection.SONGS)
        library.refresh(LibrarySection.ALBUMS)
        library.refresh(LibrarySection.ARTISTS)
        library.refresh(LibrarySection.PLAYLISTS)
        awaitCalls(4)

        assertEquals(
            setOf(
                "YOUTUBE liked [s1, s2]",
                "YOUTUBE albums [MPREb_a1, MPREb_a2]",
                "YOUTUBE artists [UC1]",
                "YOUTUBE playlists [LM]",
            ),
            repository.calls.toSet(),
        )
    }

    @Test
    fun albumsAreSavedNowInTheOrderOfTheLibrary() = runBlocking {
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(1)

        // The first one, the most recently added, is the newest
        assertEquals(
            listOf(clock.time, clock.time - 1.milliseconds),
            repository.albums.map { it.starredAt },
        )
    }

    @Test
    fun nothingHappensWhileNobodyIsSignedIn() = runBlocking {
        cookies = ""

        library.refresh(LibrarySection.ALBUMS)
        delay(200)

        assertTrue(repository.calls.isEmpty())
        assertEquals(0, requests.get())
    }

    @Test
    fun aSessionThatEndedKeepsWhatIsStored() = runBlocking {
        loggedIn = "0"

        library.refresh(LibrarySection.ALBUMS)
        withTimeout(5_000) { while (requests.get() < 1) delay(10) }
        delay(200)

        assertTrue(repository.calls.isEmpty())

        // A failure lets the next call try again at once
        loggedIn = "1"
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(1)
    }

    @Test
    fun aSectionIsNotFetchedAgainSoon() = runBlocking {
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(1)
        library.refresh(LibrarySection.ALBUMS)
        delay(200)
        assertEquals(1, requests.get())

        clock.time += 6.minutes
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(2)
    }

    @Test
    fun anotherSessionIsFetchedAtOnce() = runBlocking {
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(1)

        cookies = "SAPISID=someone-else"
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(2)
    }

    @Test
    fun anotherChannelIsFetchedAtOnce() = runBlocking {
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(1)

        pageId = "1111"
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(2)
    }

    @Test
    fun signingOutEmptiesTheYouTubeLibrary() = runBlocking {
        library.clear()

        assertEquals(
            listOf(
                "YOUTUBE liked []",
                "YOUTUBE albums []",
                "YOUTUBE artists []",
                "YOUTUBE playlists []",
            ),
            repository.calls.toList(),
        )
        assertEquals(0, requests.get())
    }

    @Test
    fun aSectionTurnedOffIsNotFetched() = runBlocking {
        syncing -= LibrarySection.ARTISTS

        library.refreshAll()
        awaitCalls(3)
        delay(200)

        assertEquals(
            setOf(
                "YOUTUBE liked [s1, s2]",
                "YOUTUBE albums [MPREb_a1, MPREb_a2]",
                "YOUTUBE playlists [LM]",
            ),
            repository.calls.toSet(),
        )
        assertEquals(3, requests.get())
    }

    @Test
    fun turningASectionOffTakesOnlyItOut() = runBlocking {
        library.clear(LibrarySection.PLAYLISTS)

        assertEquals(listOf("YOUTUBE playlists []"), repository.calls.toList())
        assertEquals(0, requests.get())
    }

    @Test
    fun aFetchRunningWhenTurnedOffIsDropped() = runBlocking {
        onRequest = { syncing -= LibrarySection.ALBUMS }

        library.refresh(LibrarySection.ALBUMS)
        withTimeout(5_000) { while (requests.get() < 1) delay(10) }
        delay(200)

        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun turningASectionBackOnFetchesItAtOnce() = runBlocking {
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(1)

        syncing -= LibrarySection.ALBUMS
        library.clear(LibrarySection.ALBUMS)
        syncing += LibrarySection.ALBUMS
        library.refresh(LibrarySection.ALBUMS)
        awaitCalls(3)

        assertEquals(
            listOf(
                "YOUTUBE albums [MPREb_a1, MPREb_a2]",
                "YOUTUBE albums []",
                "YOUTUBE albums [MPREb_a1, MPREb_a2]",
            ),
            repository.calls.toList(),
        )
    }

    @Test
    fun likedSongsAreLikedNowInTheOrderOfYouTube() = runBlocking {
        library.refresh(LibrarySection.SONGS)
        awaitCalls(1)

        assertEquals(listOf("YOUTUBE liked [s1, s2]"), repository.calls.toList())
        // The first one, the most recently liked, is the newest
        assertEquals(
            listOf(clock.time, clock.time - 1.milliseconds),
            repository.songs.map { it.starredAt },
        )
    }

    @Test
    fun likesBeyondWhatIsFetchedAreNotTakenAway() = runBlocking {
        endlessLikes = true

        library.refresh(LibrarySection.SONGS)
        awaitCalls(1)

        // Only the likes fetched are given, the others stay as they are
        assertTrue(repository.calls.single().startsWith("YOUTUBE songs [s1, s2, s3]"))
        assertTrue(repository.songs.all { it.starredAt != null })
    }

    @Test
    fun likedSongsOutsideTheSessionTakeNothingAway() = runBlocking {
        loggedIn = "0"

        library.refresh(LibrarySection.SONGS)
        withTimeout(5_000) { while (requests.get() < 1) delay(10) }
        delay(200)

        assertTrue(repository.calls.isEmpty())
    }
}
