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

package me.knighthat.kreate.provider.opensubsonic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.knighthat.kreate.constant.OpenSubsonicAuth
import me.knighthat.kreate.database.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Collections
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OpenSubsonicProviderTest {

    private class FakeSettings(
        override var enabled: Boolean = true,
        override var url: String = "https://music.example.com",
        override var auth: OpenSubsonicAuth = OpenSubsonicAuth.PASSWORD,
        override var username: String = "me",
        var password: String = "secret",
        var apiKey: String = "key123",
        override var allowSelfSigned: Boolean = false,
        override var pinnedCertificate: CertificatePin? = null,
    ) : OpenSubsonicSettings {

        override suspend fun password() = password

        override suspend fun apiKey() = apiKey

        override fun pin(pin: CertificatePin?) {
            pinnedCertificate = pin
        }
    }

    // Requests may run in parallel, like the albums of an artist
    private val requests: MutableList<HttpRequestData> =
        Collections.synchronizedList(mutableListOf())

    private fun provider(
        settings: OpenSubsonicSettings = FakeSettings(),
        respond: suspend (HttpRequestData) -> String = { OK },
    ): OpenSubsonicProvider {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = respond(request),
                status = HttpStatusCode.OK,
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
        }
        val client = HttpClient(engine)
        return OpenSubsonicProvider({ client }, Json { ignoreUnknownKeys = true }, settings)
    }

    private fun HttpRequestData.form(): Parameters = runBlocking {
        io.ktor.http.parseQueryString(body.toByteArray().decodeToString())
    }

    @Test
    fun pingDoesNotContactTheServerWhileDisabled() = runBlocking {
        val result = provider(FakeSettings(enabled = false)).ping()

        assertEquals(ServerCheck.Disabled, result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun checkingCredentialsDoesNotContactTheServerWhileDisabled() = runBlocking {
        val result = provider(FakeSettings(enabled = false)).checkCredentials()

        assertEquals(ServerCheck.Disabled, result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun pingNeedsAnAddress() = runBlocking {
        assertEquals(ServerCheck.NotConfigured, provider(FakeSettings(url = " ")).ping())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun pingSendsNoCredentialsAndReportsTheServer() = runBlocking {
        val result = provider().ping()

        val server = (result as ServerCheck.Success).server
        assertEquals("1.16.1", server.apiVersion)
        assertEquals("navidrome", server.type)
        assertEquals("0.55.0", server.serverVersion)
        assertTrue(server.isOpenSubsonic)

        val request = requests.single()
        assertEquals("https://music.example.com/rest/ping", request.url.toString())
        val form = request.form()
        assertNull(form["u"])
        assertNull(form["t"])
        assertNull(form["apiKey"])
    }

    @Test
    fun serverThatRefusesTheMissingSignInIsStillAvailable() = runBlocking {
        val result = provider { FAILED_40 }.ping()

        assertTrue(result is ServerCheck.Success)
    }

    @Test
    fun pingNoticesAnAnswerThatIsNotSubsonic() = runBlocking {
        assertEquals(ServerCheck.NotSubsonic, provider { "<html>Login</html>" }.ping())
    }

    @Test
    fun pingReportsAServerThatCannotBeReached() = runBlocking {
        val result = provider { throw IOException("refused") }.ping()

        assertEquals(ServerCheck.Unreachable("refused"), result)
    }

    @Test
    fun aChangedCertificateIsReportedWithBothFingerprints() = runBlocking {
        val changed = CertificateChangedException("music.test", "NEW", "OLD")
        val result =
            provider { throw SSLHandshakeException("refused").apply { initCause(changed) } }.ping()

        assertEquals(ServerCheck.CertificateChanged("music.test", "NEW", "OLD"), result)
    }

    @Test
    fun anUntrustedCertificateIsNotReportedAsUnreachable() = runBlocking {
        val result = provider { throw SSLHandshakeException("Trust anchor not found") }.ping()

        assertEquals(ServerCheck.UntrustedCertificate("Trust anchor not found"), result)
    }

    @Test
    fun aCertificateProblemOnAnotherAddressOfTheHostIsFound() = runBlocking {
        val result = provider {
            throw IOException("Failed to connect to [fd00::1]:443").apply {
                addSuppressed(SSLHandshakeException("Trust anchor not found"))
            }
        }.ping()

        assertEquals(ServerCheck.UntrustedCertificate("Trust anchor not found"), result)
    }

    @Test
    fun trustingAChangeRemembersTheNewCertificate() {
        val settings = FakeSettings(pinnedCertificate = CertificatePin("music.test", "OLD"))

        provider(settings).trust(ServerCheck.CertificateChanged("music.test", "NEW", "OLD"))

        assertEquals(CertificatePin("music.test", "NEW"), settings.pinnedCertificate)
    }

    @Test
    fun theServerAddressIsKnownOnlyWhileInUse() {
        assertEquals(
            "https://music.example.com",
            provider(FakeSettings(url = "https://music.example.com/")).serverUrl
        )
        assertNull(provider(FakeSettings(url = "")).serverUrl)
        assertNull(provider(FakeSettings(enabled = false)).serverUrl)
    }

    @Test
    fun theLibraryIsNotListedWhileDisabled() = runBlocking {
        val server = provider(FakeSettings(enabled = false))

        assertNull(server.libraryAlbums())
        assertNull(server.libraryArtists())
        assertNull(server.libraryPlaylists())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun albumsAreListedPageByPageWithTheirStars() = runBlocking {
        val server = provider { request ->
            val offset = request.form()["offset"]!!.toInt()
            // A full first page, then the last one
            val count = if (offset == 0) 500 else 2
            val albums = (0 until count).joinToString(",") { i ->
                val n = offset + i
                if (n == 0) """{"id":"a0","name":"First","artist":"Solo","year":2001,"starred":"2024-05-01T10:00:00Z","explicitStatus":"explicit"}"""
                else """{"id":"a$n","name":"Album $n","artists":[{"id":"x","name":"One"},{"id":"y","name":"Two"}]}"""
            }
            """{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[$albums]}}}"""
        }

        val albums = server.libraryAlbums()!!

        assertEquals(502, albums.size)
        assertEquals(listOf("0", "500"), requests.map { it.form()["offset"] })
        assertTrue(requests.all { it.form()["type"] == "alphabeticalByName" && it.form()["u"] == "me" })

        val (first, starred) = albums.first()
        assertEquals("a0", first.id)
        assertEquals(Origin.OPEN_SUBSONIC, first.origin)
        assertEquals("First", first.name)
        assertEquals(listOf("Solo"), first.artists)
        assertEquals(2001, first.year)
        assertEquals(true, first.isExplicit)
        assertEquals(Instant.parse("2024-05-01T10:00:00Z"), starred)

        val (second, notStarred) = albums[1]
        assertEquals(listOf("One", "Two"), second.artists)
        assertNull(second.isExplicit)
        assertNull(notStarred)
    }

    @Test
    fun artistsOfEveryIndexAreListed() = runBlocking {
        val server = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","artists":{"index":[
                {"name":"A","artist":[{"id":"r1","name":"Abba","starred":"2023-01-02T03:04:05.678Z"}]},
                {"name":"B","artist":[{"id":"r2","name":"Bjork"}]}]}}}"""
        }

        val artists = server.libraryArtists()!!

        assertEquals("getArtists", requests.single().url.encodedPath.substringAfterLast('/'))
        assertEquals(listOf("Abba", "Bjork"), artists.map { it.item.name })
        assertEquals("r1", artists[0].item.id)
        assertEquals(Instant.parse("2023-01-02T03:04:05.678Z"), artists[0].starredAt)
        assertNull(artists[1].starredAt)
    }

    @Test
    fun playlistsKeepTheirServerIdApart() = runBlocking {
        val server = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","playlists":{"playlist":[
                {"id":"p1","name":"Road trip","songCount":3}]}}}"""
        }

        val playlist = server.libraryPlaylists()!!.single()

        assertEquals("Road trip", playlist.name)
        assertEquals(0L, playlist.id)
        assertEquals(Origin.OPEN_SUBSONIC, playlist.origin)
        assertEquals("p1", playlist.remoteId)
    }

    @Test
    fun anAlbumComesWithItsSongsInOrder() = runBlocking {
        val (album, songs) = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{
                "id":"al1","name":"FEAR","artist":"NF","year":2025,"starred":"2024-01-01T00:00:00Z","song":[
                {"id":"s1","title":"One","albumId":"al1","album":"FEAR","track":1,"duration":200,"artistId":"ar1","artist":"NF"},
                {"id":"s2","title":"Two","albumId":"al1","album":"FEAR","track":2,"duration":181,"explicitStatus":"explicit",
                 "starred":"2024-02-03T04:05:06Z","artists":[{"id":"ar1","name":"NF"},{"id":"ar2","name":"mgk"}]}]}}}"""
        }.albumWithSongs("al1")!!

        assertEquals("al1", requests.single().form()["id"])
        assertEquals("FEAR", album.item.name)
        assertEquals(listOf("s1", "s2"), songs.map { it.song.id })

        val (one, onesAlbum, onesArtists, notStarred) = songs[0]
        assertEquals("One", one.name)
        assertEquals(Origin.OPEN_SUBSONIC, one.origin)
        assertEquals("al1", one.albumId)
        assertEquals(1, one.albumPosition)
        assertEquals(200.seconds, one.duration)
        assertEquals("FEAR", onesAlbum?.name)
        assertEquals(listOf("ar1"), onesArtists.map { it.id })
        assertNull(notStarred)

        val two = songs[1]
        assertEquals(true, two.song.isExplicit)
        assertEquals(listOf("NF", "mgk"), two.artists.map { it.name })
        assertEquals(Instant.parse("2024-02-03T04:05:06Z"), two.starredAt)
    }

    @Test
    fun anArtistComesWithTheSongsOfEveryAlbum() = runBlocking {
        val found = provider { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "getArtist" -> """{"subsonic-response":{"status":"ok","version":"1.16.1","artist":{
                    "id":"ar1","name":"NF","album":[{"id":"al1","name":"A"},{"id":"al2","name":"B"}]}}}"""

                else -> {
                    val id = request.form()["id"]
                    """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"id":"$id","name":"$id",
                        "song":[{"id":"$id-s","title":"t","albumId":"$id","artistId":"ar1"}]}}}"""
                }
            }
        }.artistWithSongs("ar1")!!

        assertEquals("NF", found.artist.item.name)
        assertEquals(setOf("al1", "al2"), found.albums.map { it.item.id }.toSet())
        assertEquals(setOf("al1-s", "al2-s"), found.songs.map { it.song.id }.toSet())
        assertEquals(3, requests.size)
    }

    @Test
    fun aPlaylistComesWithItsEntries() = runBlocking {
        val songs = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"p1","name":"Mix","entry":[
                {"id":"s9","title":"Nine"},{"id":"s3","title":"Three","albumId":"al3","album":"Three"}]}}}"""
        }.playlistSongs("p1")!!

        assertEquals("p1", requests.single().form()["id"])
        assertEquals(listOf("s9", "s3"), songs.map { it.song.id })
        assertNull(songs[0].album)
        assertEquals("al3", songs[1].album?.id)
    }

    @Test
    fun nothingIsFetchedWhileDisabled() = runBlocking {
        val server = provider(FakeSettings(enabled = false))

        assertNull(server.albumWithSongs("al1"))
        assertNull(server.artistWithSongs("ar1"))
        assertNull(server.playlistSongs("p1"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aShareIsMadeForTheItemWithItsName() = runBlocking {
        val link = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","shares":{"share":[
                {"id":"s1","url":"https://music.example.com/share/s1","description":"FEAR"}]}}}"""
        }.createShare("al-1", "FEAR")

        assertEquals("https://music.example.com/share/s1", link)
        val form = requests.single().form()
        assertEquals("al-1", form["id"])
        assertEquals("FEAR", form["description"])
        assertTrue(requests.single().url.encodedPath.endsWith("/rest/createShare"))
    }

    @Test
    fun noShareIsMadeWhileDisabled() = runBlocking {
        assertNull(provider(FakeSettings(enabled = false)).createShare("al-1", null))
        assertNull(provider(FakeSettings(enabled = false)).shareUrls())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theLinksOfExistingSharesAreListed() = runBlocking {
        val links = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","shares":{"share":[
                {"id":"s1","url":"https://music.example.com/share/s1"},
                {"id":"s2","url":"https://music.example.com/share/s2"}]}}}"""
        }.shareUrls()

        assertEquals(
            setOf(
                "https://music.example.com/share/s1",
                "https://music.example.com/share/s2"
            ), links
        )
    }

    @Test
    fun noSharesIsAnEmptyList() = runBlocking {
        val links = provider {
            """{"subsonic-response":{"status":"ok","version":"1.16.1","shares":{}}}"""
        }.shareUrls()

        assertEquals(emptySet<String>(), links)
    }

    @Test
    fun aRefusedListingThrowsWithTheReason() = runBlocking {
        val error = assertThrows(ServerException::class.java) {
            runBlocking { provider { FAILED_40 }.libraryPlaylists() }
        }

        assertEquals(ServerCheck.Rejected(40, "Wrong username or password"), error.check)
    }

    @Test
    fun acceptedCredentialsSucceed() = runBlocking {
        assertTrue(provider().checkCredentials() is ServerCheck.Success)
    }

    @Test
    fun refusedCredentialsAreRejected() = runBlocking {
        val result = provider { FAILED_40 }.checkCredentials()

        assertEquals(ServerCheck.Rejected(40, "Wrong username or password"), result)
    }

    @Test
    fun passwordSignInSendsAHashNeverThePassword() = runBlocking {
        provider().checkCredentials()

        val request = requests.single()
        val form = request.form()
        assertEquals("me", form["u"])
        assertEquals(32, form["t"]?.length)
        assertEquals(16, form["s"]?.length)
        assertNull(form["apiKey"])
        assertFalse(form.entries().any { (_, values) -> "secret" in values })
        // The address stays free of the sign in
        assertEquals("https://music.example.com/rest/ping", request.url.toString())
    }

    @Test
    fun apiKeySignInSendsOnlyTheKey() = runBlocking {
        provider(FakeSettings(auth = OpenSubsonicAuth.API_KEY)).checkCredentials()

        val form = requests.single().form()
        assertEquals("key123", form["apiKey"])
        assertNull(form["u"])
        assertNull(form["t"])
        assertNull(form["s"])
    }

    @Test
    fun credentialsOfTheOtherMethodDoNotCount() = runBlocking {
        val settings = FakeSettings(auth = OpenSubsonicAuth.API_KEY, apiKey = "")

        assertEquals(ServerCheck.NotConfigured, provider(settings).checkCredentials())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun passwordSignInNeedsBothUsernameAndPassword() = runBlocking {
        val settings = FakeSettings(username = "")

        assertEquals(ServerCheck.NotConfigured, provider(settings).checkCredentials())
        assertTrue(requests.isEmpty())
    }

    private companion object {

        const val OK =
            """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.55.0","openSubsonic":true}}"""

        const val FAILED_40 =
            """{"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome","serverVersion":"0.55.0","openSubsonic":true,"error":{"code":40,"message":"Wrong username or password"}}}"""
    }
}
