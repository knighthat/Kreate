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

package me.knighthat.kreate3.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.time.Instant

private const val FILES = "https://files.example.test"

class PatreonFilesTest {

    private val requests = mutableListOf<HttpRequestData>()

    /** Answers the requests in turn with [answers] (status, body). */
    private fun files(
        vararg answers: Pair<HttpStatusCode, String>,
        filesUrl: String = FILES
    ): PatreonFiles {
        var asked = 0
        return PatreonFiles(
            http = HttpClient(MockEngine { request ->
                val (status, body) = answers[asked++]
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            filesUrl = filesUrl,
        )
    }

    @Test
    fun `every page is listed with the token`() = runBlocking {
        val list = files(
            HttpStatusCode.OK to """{"files":[{"name":"a.zip","size":10,"lastModified":"2026-11-01T10:00:00.000Z"}],"next":"c/1"}""",
            HttpStatusCode.OK to """{"files":[{"name":"b.zip","size":20,"lastModified":"bad"},{"size":1}],"next":null}""",
        ).list("jwt")

        assertEquals(
            listOf(
                RemoteFile("a.zip", 10, Instant.parse("2026-11-01T10:00:00Z")),
                RemoteFile("b.zip", 20, null),
            ),
            list,
        )
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == "Bearer jwt" })
        assertNull(requests[0].url.parameters["cursor"])
        assertEquals("c/1", requests[1].url.parameters["cursor"])
    }

    @Test
    fun `refused token and low tier are told apart`() = runBlocking {
        try {
            files(HttpStatusCode.Unauthorized to """{"error":"invalid_token"}""").list("jwt")
            fail()
        } catch (_: PatreonTokenRefusedException) {
        }

        try {
            files(
                HttpStatusCode.Forbidden to
                        """{"error":"tier_too_low","patronStatus":"declined_patron","message":"Declined"}"""
            ).list("jwt")
            fail()
        } catch (e: PatreonTierException) {
            assertEquals(PatronStatus.DECLINED_PATRON, e.patronStatus)
            assertEquals("Declined", e.message)
        }

        try {
            files(HttpStatusCode.BadGateway to """{"error":"storage_error"}""").list("jwt")
            fail()
        } catch (e: ApiException) {
            assertEquals(502, e.status)
            assertEquals("storage_error", e.error)
        }
    }

    @Test
    fun `no address means no requests`() = runBlocking {
        try {
            files(filesUrl = "").list("jwt")
            fail()
        } catch (_: ApiUnavailableException) {
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `upload asks for a link, then puts the body without the token`() = runBlocking {
        val link = """{"url":"https://storage.example.test/put","method":"PUT",""" +
                """"headers":{"Content-Type":"application/zip","x-amz-meta":"1"},"maxBytes":100,"expiresAt":"x"}"""
        files(
            HttpStatusCode.OK to link,
            HttpStatusCode.Forbidden to "<Error/>",
            HttpStatusCode.OK to link,
            HttpStatusCode.OK to "",
        ).upload("jwt", "a.zip", byteArrayOf(1, 2, 3))

        assertEquals(4, requests.size)
        val (ask, put) = requests[2] to requests[3]
        assertEquals("a.zip", ask.url.parameters["name"])
        assertEquals("3", ask.url.parameters["size"])
        assertEquals("Bearer jwt", ask.headers[HttpHeaders.Authorization])
        assertEquals("https://storage.example.test/put", put.url.toString())
        assertNull(put.headers[HttpHeaders.Authorization])
        assertEquals("1", put.headers["x-amz-meta"])
        assertEquals(3L, put.body.contentLength)
        assertEquals("application/zip", put.body.contentType.toString())
    }

    @Test
    fun `upload refusals are told apart`() = runBlocking {
        try {
            files(HttpStatusCode.PayloadTooLarge to """{"error":"file_too_large","maxBytes":2}""")
                .upload("jwt", "a.zip", byteArrayOf(1, 2, 3))
            fail()
        } catch (e: PatreonFileTooLargeException) {
            assertEquals(2L, e.maxBytes)
        }

        try {
            files(HttpStatusCode.Forbidden to """{"error":"file_limit_reached"}""")
                .upload("jwt", "a.zip", byteArrayOf(1))
            fail()
        } catch (e: PatreonStorageFullException) {
            assertEquals("file_limit_reached", e.error)
        }

        // Storage refusing a second link too is a failure, not a loop
        val link = """{"url":"https://storage.example.test/put","headers":{}}"""
        try {
            files(
                HttpStatusCode.OK to link,
                HttpStatusCode.Forbidden to "",
                HttpStatusCode.OK to link,
                HttpStatusCode.Forbidden to "",
            ).upload("jwt", "a.zip", byteArrayOf(1))
            fail()
        } catch (e: IOException) {
            assertTrue(e !is ApiException)
        }
    }

    @Test
    fun `download gets the link, then the content without the token`() = runBlocking {
        val link = """{"url":"https://storage.example.test/get","expiresAt":"x"}"""
        val content = files(
            HttpStatusCode.OK to link,
            HttpStatusCode.Forbidden to "<Error/>",
            HttpStatusCode.OK to link,
            HttpStatusCode.OK to "zip",
        ).download("jwt", "a.zip")

        assertEquals("zip", content.decodeToString())
        assertEquals("a.zip", requests[2].url.parameters["name"])
        assertEquals("Bearer jwt", requests[2].headers[HttpHeaders.Authorization])
        assertNull(requests[3].headers[HttpHeaders.Authorization])

        try {
            files(HttpStatusCode.OK to link, HttpStatusCode.NotFound to "").download("jwt", "a.zip")
            fail()
        } catch (_: PatreonFileMissingException) {
        }
    }

    @Test
    fun `delete tells a file already gone`() = runBlocking {
        assertTrue(files(HttpStatusCode.NoContent to "").delete("jwt", "a.zip"))
        assertEquals(HttpMethod.Delete, requests[0].method)
        assertEquals("a.zip", requests[0].url.parameters["name"])
        assertEquals("Bearer jwt", requests[0].headers[HttpHeaders.Authorization])

        assertFalse(
            files(HttpStatusCode.NotFound to """{"error":"file_not_found"}""").delete(
                "jwt",
                "a.zip"
            )
        )

        try {
            files(HttpStatusCode.Unauthorized to """{"error":"invalid_token"}""").delete(
                "jwt",
                "a.zip"
            )
            fail()
        } catch (_: PatreonTokenRefusedException) {
        }
    }
}
