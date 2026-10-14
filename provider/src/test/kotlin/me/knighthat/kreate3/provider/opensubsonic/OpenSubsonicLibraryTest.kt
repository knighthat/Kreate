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
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OpenSubsonicLibraryTest {

    private class Settings(
        override var enabled: Boolean = true,
        override var url: String = "https://music.example.com",
    ) : OpenSubsonicSettings {
        override val auth = OpenSubsonicAuth.API_KEY
        override val username = ""
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
    }

    private class TestClock(var time: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
        override fun now() = time
    }

    private val settings = Settings()
    private val repository = Recorder()
    private val clock = TestClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = AtomicInteger()

    /** What the server answers, by method. */
    private var answer: suspend (method: String) -> String = { method ->
        when (method) {
            "getStarred2" -> ok(""""starred2":{"song":[{"id":"s1","starred":"2024-01-01T00:00:00Z"},{"id":"s2"}]}""")
            else -> error("Unexpected $method")
        }
    }

    private val provider = OpenSubsonicProvider(
        {
            HttpClient(MockEngine { request ->
                requests.incrementAndGet()
                respond(
                    content = answer(request.url.encodedPath.substringAfterLast('/')),
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

    private val library = OpenSubsonicLibrary(provider, repository, scope, clock) { syncing }

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
}
