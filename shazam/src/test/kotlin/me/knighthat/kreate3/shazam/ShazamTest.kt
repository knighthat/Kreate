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

package me.knighthat.kreate3.shazam

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class ShazamTest {

    /** 12 s of noise, the same every time. */
    private fun noise(): ShortArray {
        var seed = 12345
        return ShortArray(SIGNATURE_RATE * 12) {
            seed = seed * 1664525 + 1013904223
            (seed ushr 16).toShort()
        }
    }

    /** 2 s of a 1 Hz wave, slow enough for linear interpolation to land on its samples. */
    private fun tone(rate: Int): ShortArray = ShortArray(rate * 2) { i ->
        (sin(2 * PI * i / rate) * 12000).toInt().toShort()
    }

    // Same noise and expected values as Metrolist's test of the generator this one is ported from
    @Test
    fun signatureMatchesTheReference() {
        val signature = Signature.of(noise())
        assertEquals(13241, signature.uri.length)
        assertEquals(-771824122, signature.uri.hashCode())
    }

    @Test
    fun signatureHasShazamsHeader() {
        val uri = Signature.of(noise()).uri
        assertTrue(uri.startsWith("data:audio/vnd.shazam.sig;base64,gCX+y"))
    }

    @Test
    fun silenceHasNoPeaks() {
        assertEquals(0, Signature.of(ShortArray(SIGNATURE_RATE * 10)).peaks)
    }

    @Test
    fun resampleKeepsLengthAndPitch() {
        val resampled = resample(tone(44_100), 44_100)
        assertEquals(SIGNATURE_RATE * 2, resampled.size)
        val expected = tone(SIGNATURE_RATE)
        val off = expected.indices.maxOf { kotlin.math.abs(expected[it] - resampled[it]) }
        assertTrue("off by $off", off <= 2)
    }

    @Test
    fun silenceIsNotSent() = runBlocking {
        var requests = 0
        val shazam = shazam { requests++; respond("", HttpStatusCode.OK) }
        assertNull(shazam.recognize(ShortArray(SIGNATURE_RATE * 10), SIGNATURE_RATE, Locale.US))
        assertEquals(0, requests)
    }

    @Test
    fun notFoundIsNoMatch() = runBlocking {
        val shazam = shazam { respond("", HttpStatusCode.NotFound) }
        assertNull(shazam.recognize(noise(), SIGNATURE_RATE, Locale.US))
    }

    @Test(expected = ShazamException::class)
    fun tooManyRequestsThrows(): Unit = runBlocking {
        val shazam = shazam { respond("", HttpStatusCode.TooManyRequests) }
        shazam.recognize(noise(), SIGNATURE_RATE, Locale.US)
    }

    @Test
    fun requestCarriesTheSignature() = runBlocking {
        var path = ""
        var body = ""
        val shazam = shazam { request ->
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond("""{"matches":[]}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        assertNull(shazam.recognize(noise(), SIGNATURE_RATE, Locale.UK))

        assertTrue(path, path.startsWith("/discovery/v5/en/GB/android/-/tag/"))
        val signature = Json.parseToJsonElement(body).jsonObject["signature"]!!.jsonObject
        assertTrue(signature["uri"]!!.jsonPrimitive.content.startsWith("data:audio/vnd.shazam.sig"))
        assertEquals("12000", signature["samplems"]!!.jsonPrimitive.content)
    }

    @Test
    fun matchIsRead() = runBlocking {
        val shazam = shazam { respond(MATCH, HttpStatusCode.OK, JSON_HEADERS) }
        val track = shazam.recognize(noise(), SIGNATURE_RATE, Locale.US)
        assertEquals(
            ShazamTrack(
                title = "Midnight Drive",
                artist = "Neon Coast",
                album = "Afterglow",
                cover = "https://example.com/hq.jpg",
                isrc = "XX0000000001",
                explicit = true,
                shazamUrl = "https://www.shazam.com/track/1/midnight-drive",
                youTubeMusicSearch = "https://music.youtube.com/search?q=Midnight+Drive+Neon+Coast&feature=shazam",
            ),
            track
        )
    }

    private fun shazam(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ) = Shazam(HttpClient(MockEngine(handler)), Json { ignoreUnknownKeys = true })

    private companion object {
        val JSON_HEADERS = headersOf("Content-Type", "application/json")

        val MATCH = """
            {
              "matches": [{"id": "1"}],
              "track": {
                "key": "1",
                "title": "Midnight Drive",
                "subtitle": "Neon Coast",
                "isrc": "XX0000000001",
                "url": "https://www.shazam.com/track/1",
                "share": {"href": "https://www.shazam.com/track/1/midnight-drive"},
                "hub": {
                  "explicit": true,
                  "providers": [
                    {"type": "SPOTIFY", "actions": [{"uri": "spotify:search:Midnight%20Drive"}]},
                    {"type": "YOUTUBEMUSIC", "actions": [{"uri": "https://music.youtube.com/search?q=Midnight+Drive+Neon+Coast&feature=shazam"}]}
                  ]
                },
                "images": {"coverart": "https://example.com/c.jpg", "coverarthq": "https://example.com/hq.jpg"},
                "sections": [
                  {"type": "SONG", "metadata": [
                    {"title": "Album", "text": "Afterglow"},
                    {"title": "Released", "text": "2020"}
                  ]},
                  {"type": "LYRICS"}
                ]
              }
            }
        """.trimIndent()
    }
}
