package com.metrolist.innertubex.cipher

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestrictedHttpFetchTest {
    @Test
    fun doesNotFollowRedirects() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = "redirect",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, "https://example.com/untrusted.js"),
                    )
                }
            val client = HttpClient(engine)

            val response = client.getTextWithoutRedirects(Url("https://www.youtube.com/s/player/hash/base.js"), 1024)

            assertEquals(HttpStatusCode.Found, response.status)
            assertNull(response.body)
            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun doesNotReadNonSuccessBodies() =
        runBlocking {
            val engine = MockEngine { respond("{\"valid\":true}", HttpStatusCode.NotFound) }
            val client = HttpClient(engine)

            val response = client.getTextWithoutRedirects(Url("https://raw.githubusercontent.com/test/config.json"), 1024)

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertNull(response.body)
        }
}
