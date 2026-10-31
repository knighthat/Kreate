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

package me.knighthat.kreate3.musicbrainz

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.musicbrainz.MusicBrainz.Companion.best
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MusicBrainzTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Every request is answered by [answer], and kept in [requests]. */
    private val requests = mutableListOf<HttpRequestData>()

    private fun musicBrainz(answer: (HttpRequestData) -> Pair<HttpStatusCode, String>) =
        MusicBrainz(
            HttpClient(MockEngine { request ->
                requests += request
                val (status, body) = answer(request)
                respond(body, status)
            }),
            json,
        )

    private fun match(
        id: String,
        score: Int = 100,
        length: Long? = 200_000,
        releases: Int = 1,
        firstReleased: String? = null,
        disambiguation: String? = null,
    ) = MusicBrainzMatch(
        id, "Song", "Artist", length?.milliseconds, firstReleased, disambiguation, releases, score
    )

    @Test
    fun bestPrefersTheSameLengthThenStudioThenMostReleasedThenOldest() {
        val matches = listOf(
            match("short", length = 120_000, releases = 50),
            match("live", disambiguation = "live, 1986", releases = 40),
            match("compilation", releases = 2, firstReleased = "2001"),
            match("original", releases = 2, firstReleased = "1975"),
            match("poor", score = 60, releases = 99),
        )

        assertEquals("original", matches.best("Song", 201.seconds)?.id)
        // Asked for a live song, a live recording is kept
        assertEquals("live", matches.best("Song (Live)", 201.seconds)?.id)
        // Nothing as long: the length is ignored
        assertEquals("short", matches.best("Song", 999.seconds)?.id)
        assertNull(emptyList<MusicBrainzMatch>().best("Song", null))
    }

    @Test
    fun searchFallsBackToLooseWordsAndReadsTheCredit() = runBlocking {
        val found = """{"recordings":[{"id":"r1","score":97,"title":"Song","length":200000,
            "first-release-date":"1999","artist-credit":[{"name":"A","joinphrase":" feat. "},
            {"name":"B"}],"releases":[{"id":"x"},{"id":"y"}]}]}"""
        val musicBrainz = musicBrainz { request ->
            val query = request.url.parameters["query"].orEmpty()
            HttpStatusCode.OK to (if (query.contains('"')) """{"recordings":[]}""" else found)
        }

        val matches = musicBrainz.search("Song (Remix)", "A")

        assertEquals(
            listOf(MusicBrainzMatch("r1", "Song", "A feat. B", 200.seconds, "1999", null, 2, 97)),
            matches,
        )
        assertEquals(
            "recording:\"Song (Remix)\" AND artist:\"A\"",
            requests[0].url.parameters["query"]
        )
        assertEquals(
            "recording:(Song \\(Remix\\)) AND artist:(A)",
            requests[1].url.parameters["query"]
        )
        assertTrue(requests.all { it.headers["User-Agent"].orEmpty().startsWith("Kreate/") })
    }

    @Test
    fun recordingReadsReleasesOldestFirstAndGenresByVotes() = runBlocking {
        val body = """{"id":"r1","title":"Song","length":200000,"disambiguation":"",
            "isrcs":["GB0000000001"],"artist-credit":[{"name":"A"}],
            "genres":[{"name":"rock","count":1},{"name":"pop","count":3}],
            "tags":[{"name":"rock","count":5},{"name":"80s","count":2}],
            "releases":[
              {"id":"b","title":"Hits","date":"2001","country":"XW","status":"Official",
               "release-group":{"primary-type":"Album"}},
              {"id":"c","title":"Undated"},
              {"id":"a","title":"Song","date":"1999-05","country":"GB",
               "release-group":{"primary-type":"Single"}}
            ]}"""
        val recording = musicBrainz { HttpStatusCode.OK to body }.recording("r1")!!

        assertEquals("A", recording.artists)
        assertNull(recording.disambiguation)
        assertEquals("1999-05", recording.firstReleased)
        assertEquals(listOf("GB0000000001"), recording.isrcs)
        assertEquals(listOf("pop", "rock", "80s"), recording.genres)
        assertEquals(listOf("a", "b", "c"), recording.releases.map { it.id })
        assertEquals("Single", recording.releases.first().type)
    }

    @Test
    fun aMissingRecordingIsNullAndOtherErrorsThrow() = runBlocking {
        assertNull(musicBrainz { HttpStatusCode.NotFound to "" }.recording("gone"))
        val failing = musicBrainz { HttpStatusCode.BadRequest to "" }
        assertThrows(IOException::class.java) { runBlocking { failing.recording("r1") } }
        Unit
    }
}
