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
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Remembers what it was asked to store, as `<method> <ids>`. */
    private class Recorder : RemoteCatalogRepository {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun syncAlbums(origin: Origin, albums: List<RemoteItem<Album>>) {
            calls += "albums ${albums.map { it.item.id }}"
        }

        override suspend fun syncArtists(origin: Origin, artists: List<RemoteItem<Artist>>) {
            calls += "artists ${artists.map { it.item.id }}"
        }

        override suspend fun syncPlaylists(origin: Origin, playlists: List<UserPlaylist>) {
            calls += "playlists ${playlists.map { it.remoteId }}"
        }

        override suspend fun syncSongs(
            origin: Origin,
            songs: List<RemoteSong>,
            albums: List<RemoteItem<Album>>,
            artists: List<RemoteItem<Artist>>,
            playlistId: Long?,
        ) {
            calls += "songs ${songs.map { it.song.id }} albums ${albums.map { it.item.id }} " +
                    "artists ${artists.map { it.item.id }} playlist $playlistId"
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
            "getAlbumList2" -> ok(""""albumList2":{"album":[{"id":"a1"},{"id":"a2"}]}""")
            "getArtists" -> ok(""""artists":{"index":[{"name":"A","artist":[{"id":"r1"}]}]}""")
            "getPlaylists" -> ok(""""playlists":{"playlist":[{"id":"p1","name":"Mix"}]}""")
            "getAlbum" -> ok(""""album":{"id":"a1","song":[{"id":"s1"},{"id":"s2"}]}""")
            "getArtist" -> ok(""""artist":{"id":"r1","album":[{"id":"a1"}]}""")
            "getPlaylist" -> ok(""""playlist":{"id":"p1","entry":[{"id":"s3"}]}""")
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

    private val library = OpenSubsonicLibrary(provider, repository, scope, clock)

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
    fun everySectionIsStored() {
        library.refresh(LibrarySection.ALBUMS)
        library.refresh(LibrarySection.ARTISTS)
        library.refresh(LibrarySection.PLAYLISTS)

        eventually { repository.calls.size == 3 }
        assertEquals(
            setOf("albums [a1, a2]", "artists [r1]", "playlists [p1]"),
            repository.calls.toSet()
        )
    }

    @Test
    fun aSectionIsNotRefreshedAgainSoon() {
        library.refresh(LibrarySection.ALBUMS)
        eventually { repository.calls.size == 1 }

        clock.time += 4.minutes
        library.refresh(LibrarySection.ALBUMS)
        settle()
        assertEquals(1, requests.get())

        clock.time += 1.minutes
        library.refresh(LibrarySection.ALBUMS)
        eventually { repository.calls.size == 2 }
    }

    @Test
    fun aRefreshThatIsRunningIsNotStartedTwice() {
        val release = CompletableDeferred<Unit>()
        val default = answer
        answer = { release.await(); default(it) }

        library.refresh(LibrarySection.ALBUMS)
        library.refresh(LibrarySection.ALBUMS)
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
        library.refresh(LibrarySection.ALBUMS)
        eventually { requests.get() == 1 }
        settle()
        assertTrue(repository.calls.isEmpty())

        answer = default
        library.refresh(LibrarySection.ALBUMS)

        eventually { repository.calls.size == 1 }
    }

    @Test
    fun nothingIsRefreshedWhileTheServerIsOff() {
        settings.enabled = false

        library.refresh(LibrarySection.ALBUMS)
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

        library.refresh(LibrarySection.ALBUMS)
        eventually { requests.get() == 1 }
        settle()
        assertTrue(repository.calls.isEmpty())

        // The new address is a section of its own, refreshed at once
        answer = default
        library.refresh(LibrarySection.ALBUMS)
        eventually { repository.calls.size == 1 }
    }

    @Test
    fun anAlbumIsStoredWithItsSongs() = runBlocking {
        assertTrue(library.syncAlbum("a1"))

        assertEquals(
            listOf("songs [s1, s2] albums [a1] artists [] playlist null"),
            repository.calls
        )
    }

    @Test
    fun anArtistIsStoredWithTheirAlbumsAndSongs() = runBlocking {
        assertTrue(library.syncArtist("r1"))

        assertEquals(
            listOf("songs [s1, s2] albums [a1] artists [r1] playlist null"),
            repository.calls
        )
    }

    @Test
    fun aPlaylistsSongsGoToItsRow() = runBlocking {
        assertTrue(library.syncPlaylist(7, "p1"))

        assertEquals(listOf("songs [s3] albums [] artists [] playlist 7"), repository.calls)
    }

    @Test
    fun itemsAreNotStoredWhileTheServerIsOff() = runBlocking {
        settings.enabled = false

        assertFalse(library.syncAlbum("a1"))
        assertFalse(library.syncArtist("r1"))
        assertFalse(library.syncPlaylist(7, "p1"))
        assertTrue(repository.calls.isEmpty())
        assertEquals(0, requests.get())
    }

    @Test
    fun aRefusedItemThrowsWhatTheServerSaid() {
        answer =
            { """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":70,"message":"Not found"}}}""" }

        val error = runCatching { runBlocking { library.syncAlbum("a1") } }.exceptionOrNull()

        assertEquals(ServerCheck.Rejected(70, "Not found"), (error as ServerException).check)
        assertTrue(repository.calls.isEmpty())
    }
}
