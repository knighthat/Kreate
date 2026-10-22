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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class YouTubeLibraryTest {

    /** Remembers what it was asked to store. */
    private class Recorder : RemoteCatalogRepository {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val songs: MutableList<RemoteSong> = Collections.synchronizedList(mutableListOf())

        override suspend fun syncLikedSongs(
            origin: Origin,
            songs: List<RemoteSong>,
            complete: Boolean,
        ) {
            calls += "$origin liked ${songs.map { it.song.id }}" + if (complete) "" else " (partly)"
            this.songs += songs
        }

        override suspend fun savePlayedSong(song: RemoteSong) = error("Not played here")
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

    /** Whether syncing the liked songs is on. */
    @Volatile
    private var syncing = true

    /** Whether "Liked Music" has more parts than are fetched. */
    @Volatile
    private var endlessLikes = false

    /** Runs when a request arrives, before it is answered. */
    @Volatile
    private var onRequest: () -> Unit = {}

    /** The likes and dislikes sent to YouTube, as the endpoint and the song id. */
    private val rated: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** What YouTube answers to a like or dislike. */
    @Volatile
    private var rateStatus = HttpStatusCode.OK

    private val client = HttpClient(MockEngine { request ->
                requests.incrementAndGet()
                onRequest()
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString())
        val endpoint = request.url.encodedPath.substringAfter("/youtubei/v1/")
        val rating = endpoint.startsWith("like/")
        if (rating) rated += "$endpoint ${body.path("target", "videoId").asString}"
                respond(
                    content = when {
                        rating -> """{"responseContext":$signedIn}"""
                        body.path("continuation") != null -> moreLikes()
                        else -> answer(body.path("browseId").asString)
                    },
                    status = if (rating) rateStatus else HttpStatusCode.OK,
                    headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
                )
    })

    private val provider = YouTubeProvider(
        InnerTube(
            client = { client },
            json = Json,
            locale = { Locale.US },
            cookies = { cookies },
            clock = clock,
            pageId = { pageId },
        ),
        YouTubeParser { null },
    )

    /** Keeps the reaction of each song, the rest of the library isn't used here. */
    private class Reactions : UserSongRepository by unused() {
        val likedAt: MutableMap<String, Instant?> = Collections.synchronizedMap(mutableMapOf())

        override suspend fun update(songId: String, transform: (UserSong) -> UserSong) {
            likedAt[songId] = transform(UserSong(songId, likedAt = likedAt[songId])).likedAt
        }
    }

    private val reactions = Reactions()

    private val library = YouTubeLibrary(provider, repository, reactions, scope, clock) { syncing }

    private val liked = Instant.parse("2024-03-04T05:06:07Z")
    private val disliked = Instant.fromEpochMilliseconds(-1)

    private fun answer(browseId: String?): String {
        check(browseId == "VLLM") { "Unexpected $browseId" }
        return likes()
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

    private suspend fun awaitCalls(count: Int) = withTimeout(5_000) {
        while (repository.calls.size < count) delay(10)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun likedSongsAreGivenAsYouTubeListsThem() = runBlocking {
        library.refresh()
        awaitCalls(1)

        assertEquals(listOf("YOUTUBE liked [s1, s2]"), repository.calls.toList())
        // YouTube tells no like time, the repository picks one
        assertTrue(repository.songs.all { it.starredAt == null })
    }

    @Test
    fun nothingHappensWhileNobodyIsSignedIn() = runBlocking {
        cookies = ""

        library.refresh()
        delay(200)

        assertTrue(repository.calls.isEmpty())
        assertEquals(0, requests.get())
    }

    @Test
    fun aSessionThatEndedKeepsWhatIsStored() = runBlocking {
        loggedIn = "0"

        library.refresh()
        withTimeout(5_000) { while (requests.get() < 1) delay(10) }
        delay(200)

        assertTrue(repository.calls.isEmpty())

        // A failure lets the next call try again at once
        loggedIn = "1"
        library.refresh()
        awaitCalls(1)
    }

    @Test
    fun theSongsAreNotFetchedAgainSoon() = runBlocking {
        library.refresh()
        awaitCalls(1)
        library.refresh()
        delay(200)
        assertEquals(1, requests.get())

        clock.time += 6.minutes
        library.refresh()
        awaitCalls(2)
    }

    @Test
    fun refreshingNowFetchesAtOnce() = runBlocking {
        library.refresh()
        awaitCalls(1)

        library.refreshNow()
        awaitCalls(2)
    }

    @Test
    fun anotherSessionIsFetchedAtOnce() = runBlocking {
        library.refresh()
        awaitCalls(1)

        cookies = "SAPISID=someone-else"
        library.refresh()
        awaitCalls(2)
    }

    @Test
    fun anotherChannelIsFetchedAtOnce() = runBlocking {
        library.refresh()
        awaitCalls(1)

        pageId = "1111"
        library.refresh()
        awaitCalls(2)
    }

    @Test
    fun nothingIsFetchedWhileTurnedOff() = runBlocking {
        syncing = false

        library.refreshNow()
        delay(200)

        assertTrue(repository.calls.isEmpty())
        assertEquals(0, requests.get())
    }

    @Test
    fun aFetchRunningWhenTurnedOffIsDropped() = runBlocking {
        onRequest = { syncing = false }

        library.refresh()
        withTimeout(5_000) { while (requests.get() < 1) delay(10) }
        delay(200)

        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun turningOffTakesNoLikeAway() = runBlocking {
        library.refresh()
        awaitCalls(1)

        syncing = false
        library.refreshNow()
        delay(200)

        // Nothing told the repository to take the likes away
        assertEquals(listOf("YOUTUBE liked [s1, s2]"), repository.calls.toList())
    }

    @Test
    fun turningBackOnFetchesAtOnce() = runBlocking {
        library.refresh()
        awaitCalls(1)

        syncing = false
        syncing = true
        library.refreshNow()
        awaitCalls(2)

        assertEquals(
            listOf("YOUTUBE liked [s1, s2]", "YOUTUBE liked [s1, s2]"),
            repository.calls.toList(),
        )
    }

    @Test
    fun likesBeyondWhatIsFetchedAreNotTakenAway() = runBlocking {
        endlessLikes = true

        library.refresh()
        awaitCalls(1)

        // Only the likes fetched are given, the others stay as they are
        assertTrue(repository.calls.single().startsWith("YOUTUBE liked [s1, s2, s3"))
        assertTrue(repository.calls.single().endsWith("(partly)"))
    }

    @Test
    fun likedSongsOutsideTheSessionTakeNothingAway() = runBlocking {
        loggedIn = "0"

        library.refresh()
        withTimeout(5_000) { while (requests.get() < 1) delay(10) }
        delay(200)

        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun likesAndDislikesAreSentAsTheyNowAre() = runBlocking {
        library.react("s9") { liked }
        // A dislike replaces the like on YouTube
        library.react("s9") { disliked }
        library.react("s9") { null }
        library.react("s9") { disliked }
        library.react("s9") { liked }

        assertEquals(
            listOf(
                "like/like s9",
                "like/dislike s9",
                "like/removelike s9",
                "like/dislike s9",
                "like/like s9",
            ),
            rated.toList(),
        )
        assertEquals(liked, reactions.likedAt["s9"])
    }

    @Test
    fun aReactionThatDoesNotChangeSendsNothing() = runBlocking {
        reactions.likedAt["s9"] = liked

        library.react("s9") { Instant.fromEpochMilliseconds(5) }

        assertTrue(rated.isEmpty())
    }

    @Test
    fun aReactionWhileSignedOutStaysOnTheDevice() = runBlocking {
        cookies = ""

        library.react("s9") { disliked }

        assertEquals(0, requests.get())
        assertEquals(disliked, reactions.likedAt["s9"])
    }

    @Test
    fun aReactionIsNotSentWhileTheSyncingIsOff() = runBlocking {
        syncing = false

        library.react("s9") { liked }

        assertEquals(0, requests.get())
        assertEquals(liked, reactions.likedAt["s9"])
    }

    @Test
    fun aDislikeYouTubeRefusesIsTakenBack() {
        reactions.likedAt["s9"] = liked
        rateStatus = HttpStatusCode.InternalServerError

        assertThrows(YouTubeException::class.java) {
            runBlocking { library.react("s9") { disliked } }
        }

        assertEquals(liked, reactions.likedAt["s9"])
    }
}

/** A [T] that fails at every call, for the parts a test doesn't use. */
private inline fun <reified T> unused(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("${method.name} isn't used here")
    } as T
