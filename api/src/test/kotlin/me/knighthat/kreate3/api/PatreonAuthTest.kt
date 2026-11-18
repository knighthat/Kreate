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
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import kotlin.io.encoding.Base64

private const val API = "https://api.example.test"
private const val REDIRECT = "kreate3://patreon/done"

class PatreonAuthTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun auth(status: HttpStatusCode, body: String, apiUrl: String = API) = PatreonAuth(
        http = HttpClient(MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        redirect = REDIRECT,
        apiUrl = apiUrl,
    )

    private fun bodyOf(request: HttpRequestData) =
        Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    @Test
    fun `login address carries the redirect and the verifier's challenge`() {
        val signIn = auth(HttpStatusCode.OK, "").start()
        val url = Url(signIn.url)

        assertEquals("/patreon/login", url.encodedPath)
        assertEquals(REDIRECT, url.parameters["redirect"])
        assertEquals(43, signIn.verifier.length)
        val challenge = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            MessageDigest.getInstance("SHA-256").digest(signIn.verifier.toByteArray())
        )
        assertEquals(challenge, url.parameters["challenge"])
    }

    @Test
    fun `browser code is traded for tokens`() = runBlocking {
        val auth = auth(HttpStatusCode.OK, """{"token":"t","refresh":"r"}""")
        val signIn = auth.start()

        val login = auth.finish(signIn, "$REDIRECT?code=abc") as PatreonLogin.Success

        assertEquals("t", login.tokens.token)
        assertEquals("r", login.tokens.refresh)
        val sent = bodyOf(requests.single())
        assertEquals("abc", sent["code"]!!.jsonPrimitive.content)
        assertEquals(signIn.verifier, sent["verifier"]!!.jsonPrimitive.content)
        assertEquals("$API/patreon/token", requests.single().url.toString())
        Unit
    }

    @Test
    fun `browser answers without a code`() = runBlocking {
        val auth = auth(HttpStatusCode.OK, "")
        val signIn = auth.start()

        assertNull(auth.finish(signIn, "kreate3://other?code=abc"))
        assertEquals(PatreonLogin.Declined, auth.finish(signIn, "$REDIRECT?error=access_denied"))
        assertEquals(PatreonLogin.Expired, auth.finish(signIn, "$REDIRECT?error=invalid_state"))
        assertEquals(PatreonLogin.Failed, auth.finish(signIn, "$REDIRECT?error=server_error"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `stale code restarts the sign in`() = runBlocking {
        val auth = auth(HttpStatusCode.BadRequest, """{"error":"invalid_grant"}""")
        assertEquals(PatreonLogin.Expired, auth.finish(auth.start(), "$REDIRECT?code=abc"))
    }

    @Test
    fun `web view reads the fragment of the done page only`() {
        val auth = auth(HttpStatusCode.OK, "")

        assertNull(auth.webViewResult("$API/patreon/login"))
        assertNull(auth.webViewResult("https://www.patreon.com/patreon/done#token=t&refresh=r"))
        val login = auth.webViewResult("$API/patreon/done#token=a.b%2Bc&refresh=r%3D")
                as PatreonLogin.Success
        assertEquals("a.b+c", login.tokens.token)
        assertEquals("r=", login.tokens.refresh)
        assertEquals(
            PatreonLogin.Expired,
            auth.webViewResult("$API/patreon/done#error=invalid_state")
        )
    }

    @Test
    fun `refresh rotates the blob`() = runBlocking {
        val tokens = auth(HttpStatusCode.OK, """{"token":"t2","refresh":"r2"}""").refresh("r1")

        assertEquals("t2", tokens.token)
        assertEquals("r2", tokens.refresh)
        assertEquals("r1", bodyOf(requests.single())["refresh"]!!.jsonPrimitive.content)
    }

    @Test
    fun `only a 401 ends the sign in`() = runBlocking {
        try {
            auth(HttpStatusCode.Unauthorized, """{"error":"invalid_refresh"}""").refresh("r")
            fail()
        } catch (_: PatreonSignedOutException) {
        }
        try {
            auth(HttpStatusCode.BadGateway, """{"error":"patreon_error"}""").refresh("r")
            fail()
        } catch (e: ApiException) {
            assertEquals(502, e.status)
            assertEquals("patreon_error", e.error)
        }
    }

    @Test
    fun `no address means no requests`() = runBlocking {
        val auth = auth(HttpStatusCode.OK, "", apiUrl = "")
        assertNull(auth.webViewResult("$API/patreon/done#token=t&refresh=r"))
        try {
            auth.refresh("r")
            fail()
        } catch (_: ApiUnavailableException) {
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `claims are read from the payload`() {
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            """{"sub":"1","tier":3,"patron_status":"declined_patron","exp":100}""".toByteArray()
        )
        assertEquals(
            PatreonClaims("1", 3, PatronStatus.DECLINED_PATRON, 100),
            PatreonTokens("h.$payload.s", "r").claims,
        )

        val unknownStatus = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"tier":0,"patron_status":"new_value"}""".toByteArray())
        assertEquals(PatronStatus.NONE, PatreonClaims.of("h.$unknownStatus.s").status)
        assertEquals(PatreonClaims.UNKNOWN, PatreonClaims.of("not a token"))
    }
}
