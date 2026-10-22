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

package me.knighthat.kreate3.provider.opensubsonic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A [T] that fails at every call, for the parts a test doesn't use. */
private inline fun <reified T> unused(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("${method.name} isn't used here")
    } as T

class OpenSubsonicLibraryTest {

    private class Settings(
        override var enabled: Boolean = true,
        override var url: String = "https://music.example.com",
    ) : OpenSubsonicSettings {
        override val auth = OpenSubsonicAuth.API_KEY
        override suspend fun username() = ""
        override val allowSelfSigned = false
        override val pinnedCertificate: CertificatePin? = null
        override suspend fun password() = ""
        override suspend fun apiKey() = "key"
        override fun pin(pin: CertificatePin?) = Unit
    }

    /** Remembers what it was asked to store, as the ids of the liked songs. */
    private class Recorder : RemoteCatalogRepository {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun syncLikedSongs(
            origin: Origin,
            songs: List<RemoteSong>,
            complete: Boolean,
        ) {
            calls += "liked ${songs.map { it.song.id }} complete $complete"
        }

        override suspend fun savePlayedSong(song: RemoteSong) = error("Not played here")
    }

    /** Keeps the reaction of each song, the rest of the library isn't used here. */
    private class Reactions : UserSongRepository by unused() {
        val likedAt: MutableMap<String, Instant?> = Collections.synchronizedMap(mutableMapOf())

        override suspend fun update(songId: String, transform: (UserSong) -> UserSong) {
            likedAt[songId] = transform(UserSong(songId, likedAt = likedAt[songId])).likedAt
        }
    }

    private class TestClock(var time: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
        override fun now() = time
    }

    private val settings = Settings()
    private val repository = Recorder()
    private val reactions = Reactions()
    private val clock = TestClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = AtomicInteger()

    /** The stars sent to the server, as the method and the song id. */
    private val sent: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val liked = Instant.parse("2024-03-04T05:06:07Z")
    private val disliked = Instant.fromEpochMilliseconds(-1)

    /** What the server answers, by method. */
    private var answer: suspend (method: String) -> String = { method ->
        when (method) {
            "star", "unstar" -> OK
            "getStarred2" -> ok(""""starred2":{"song":[{"id":"s1","starred":"2024-01-01T00:00:00Z"},{"id":"s2"}]}""")
            else -> error("Unexpected $method")
        }
    }

    private val provider = OpenSubsonicProvider(
        {
            HttpClient(MockEngine { request ->
                requests.incrementAndGet()
                val method = request.url.encodedPath.substringAfterLast('/')
                if (method == "star" || method == "unstar") {
                    val form = parseQueryString(request.body.toByteArray().decodeToString())
                    sent += "$method ${form["id"]}"
                }
                respond(
                    content = answer(method),
                    status = HttpStatusCode.OK,
                    headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
                )
            })
        },
        Json { ignoreUnknownKeys = true },
        settings,
    )

    /** Whether syncing the starred songs is on. */
    @Volatile
    private var syncing = true

    private val library =
        OpenSubsonicLibrary(provider, repository, reactions, scope, clock) { syncing }

    private fun ok(content: String) =
        """{"subsonic-response":{"status":"ok","version":"1.16.1",$content}}"""

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    /** Gives a refresh that should do nothing the time to do it anyway. */
    private fun settle() = Thread.sleep(200)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun theStarredSongsAreLiked() {
        library.refresh()

        eventually { repository.calls.size == 1 }
        assertEquals(listOf("liked [s1, s2] complete true"), repository.calls)
    }

    @Test
    fun theSongsAreNotRefreshedAgainSoon() {
        library.refresh()
        eventually { repository.calls.size == 1 }

        clock.time += 4.minutes
        library.refresh()
        settle()
        assertEquals(1, requests.get())

        clock.time += 1.minutes
        library.refresh()
        eventually { repository.calls.size == 2 }
    }

    @Test
    fun aRefreshThatIsRunningIsNotStartedTwice() {
        val release = CompletableDeferred<Unit>()
        val default = answer
        answer = { release.await(); default(it) }

        library.refresh()
        library.refresh()
        settle()
        release.complete(Unit)

        eventually { repository.calls.isNotEmpty() }
        settle()
        assertEquals(1, requests.get())
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun aFailedRefreshCanBeTriedAgainAtOnce() {
        val default = answer
        answer =
            { """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":0,"message":"Busy"}}}""" }
        library.refresh()
        eventually { requests.get() == 1 }
        settle()
        assertTrue(repository.calls.isEmpty())

        answer = default
        library.refresh()

        eventually { repository.calls.size == 1 }
    }

    @Test
    fun nothingIsRefreshedWhileTheServerIsOff() {
        settings.enabled = false

        library.refresh()
        settle()

        assertEquals(0, requests.get())
    }

    @Test
    fun aLibraryOfAnAddressNoLongerInUseIsNotStored() {
        val default = answer
        answer = { method ->
            // The user moves to another server while the old one answers
            settings.url = "https://other.example.com"
            default(method)
        }

        library.refresh()
        eventually { requests.get() == 1 }
        settle()
        assertTrue(repository.calls.isEmpty())

        // The new address is refreshed at once
        answer = default
        library.refresh()
        eventually { repository.calls.size == 1 }
    }

    @Test
    fun nothingIsSyncedWhileTurnedOff() {
        syncing = false

        library.refresh()
        settle()

        assertEquals(0, requests.get())
    }

    @Test
    fun aFetchRunningWhenTurnedOffIsDropped() {
        val default = answer
        answer = { method ->
            syncing = false
            default(method)
        }

        library.refresh()
        eventually { requests.get() == 1 }
        settle()

        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun refreshingNowFetchesAtOnce() {
        library.refresh()
        eventually { repository.calls.size == 1 }

        library.refreshNow()
        eventually { repository.calls.size == 2 }
    }

    @Test
    fun aLikeStarsTheSongOnTheServer() = runBlocking {
        library.react("s9") { liked }

        assertEquals(listOf("star s9"), sent)
        assertEquals(liked, reactions.likedAt["s9"])
    }

    @Test
    fun aLikeTakenAwayTakesTheStarAway() = runBlocking {
        reactions.likedAt["s9"] = liked

        library.react("s9") { null }

        assertEquals(listOf("unstar s9"), sent)
        assertNull(reactions.likedAt["s9"])
    }

    @Test
    fun aDislikeStaysOnTheDevice() = runBlocking {
        library.react("s9") { disliked }
        assertEquals(disliked, reactions.likedAt["s9"])

        library.react("s9") { null }

        assertTrue(sent.isEmpty())
    }

    @Test
    fun dislikingALikedSongTakesItsStarAway() = runBlocking {
        reactions.likedAt["s9"] = liked

        library.react("s9") { disliked }

        assertEquals(listOf("unstar s9"), sent)
        assertEquals(disliked, reactions.likedAt["s9"])
    }

    @Test
    fun likingADislikedSongStarsIt() = runBlocking {
        reactions.likedAt["s9"] = disliked

        library.react("s9") { liked }

        assertEquals(listOf("star s9"), sent)
    }

    @Test
    fun aLikeThatStaysALikeSendsNothing() = runBlocking {
        reactions.likedAt["s9"] = liked

        library.react("s9") { Instant.fromEpochMilliseconds(5) }

        assertTrue(sent.isEmpty())
    }

    @Test
    fun nothingIsSentWhileTurnedOff() = runBlocking {
        syncing = false

        library.react("s9") { liked }

        assertTrue(sent.isEmpty())
        assertEquals(liked, reactions.likedAt["s9"])
    }

    @Test
    fun nothingIsSentWhileTheServerIsOff() = runBlocking {
        settings.enabled = false

        library.react("s9") { liked }

        assertEquals(0, requests.get())
        assertEquals(liked, reactions.likedAt["s9"])
    }

    @Test
    fun aLikeTheServerRefusesIsTakenBack() {
        answer =
            { """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":70,"message":"Not found"}}}""" }

        val error = assertThrows(ServerException::class.java) {
            runBlocking { library.react("s9") { liked } }
        }

        assertTrue(error.check is ServerCheck.Rejected)
        assertNull(reactions.likedAt["s9"])
    }

    @Test
    fun aDislikeTheServerCannotBeToldOfIsTakenBack() {
        reactions.likedAt["s9"] = liked
        answer = { throw IOException("No route to host") }

        val error = assertThrows(ServerException::class.java) {
            runBlocking { library.react("s9") { disliked } }
        }

        assertTrue(error.check is ServerCheck.Unreachable)
        assertEquals(liked, reactions.likedAt["s9"])
    }

    @Test
    fun aFailureDoesNotTakeBackALaterReaction() {
        val sending = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val default = answer
        answer = { method ->
            if (method == "star") {
                sending.complete(Unit)
                release.await()
                """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":0,"message":"Busy"}}}"""
            } else default(method)
        }

        val liking = scope.async { runCatching { library.react("s9") { liked } } }
        runBlocking {
            sending.await()
            // The user dislikes the song before the server answers the like
            library.react("s9") { disliked }
            release.complete(Unit)
            assertTrue(liking.await().isFailure)
        }

        assertEquals(disliked, reactions.likedAt["s9"])
    }

    @Test
    fun starredSongsFetchedBeforeAReactionWasSentAreNotStored() {
        val asked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val default = answer
        answer = { method ->
            if (method == "getStarred2") {
                asked.complete(Unit)
                release.await()
            }
            default(method)
        }

        library.refresh()
        runBlocking {
            asked.await()
            // The server answers the refresh with what it had before the like
            library.react("s9") { liked }
        }
        release.complete(Unit)
        settle()
        assertTrue(repository.calls.isEmpty())

        // The next refresh isn't held back
        answer = default
        library.refresh()
        eventually { repository.calls.size == 1 }
    }

    @Test
    fun starredSongsFetchedWhileAReactionIsSentAreNotStored() {
        val sending = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val default = answer
        answer = { method ->
            if (method == "star") {
                sending.complete(Unit)
                release.await()
            }
            default(method)
        }

        val reacting = scope.launch { library.react("s9") { liked } }
        runBlocking { sending.await() }
        library.refresh()
        eventually { requests.get() == 2 }
        settle()
        assertTrue(repository.calls.isEmpty())

        release.complete(Unit)
        runBlocking { reacting.join() }
        assertEquals(listOf("star s9"), sent)
    }

    private companion object {
        const val OK = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
    }
}
