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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.knighthat.kreate.constant.OpenSubsonicAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class OpenSubsonicProviderTest {

    private class FakeSettings(
        override var enabled: Boolean = true,
        override var url: String = "https://music.example.com",
        override var auth: OpenSubsonicAuth = OpenSubsonicAuth.PASSWORD,
        override var username: String = "me",
        override var password: String = "secret",
        override var apiKey: String = "key123",
        override var allowSelfSigned: Boolean = false,
        override var pinnedCertificate: CertificatePin? = null,
    ) : OpenSubsonicSettings {

        override fun pin(pin: CertificatePin?) {
            pinnedCertificate = pin
        }
    }

    private val requests = mutableListOf<HttpRequestData>()

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
