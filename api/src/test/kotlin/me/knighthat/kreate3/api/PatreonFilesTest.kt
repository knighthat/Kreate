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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
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
}
