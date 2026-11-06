package com.metrolist.innertubex

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptionsTest {
    @Test
    fun captionFetchAcceptsOnlyBoundedApprovedEndpoints() {
        runBlocking {
            val engine =
                MockEngine { request ->
                    assertEquals("www.youtube.com", request.url.host)
                    respond("caption", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "7"))
                }
            val innerTube = InnerTube(HttpClient(engine))

            assertEquals("caption", innerTube.fetchCaptionText("https://www.youtube.com/api/timedtext?v=abcdefghijk"))
            assertFailsWith<IllegalArgumentException> {
                innerTube.fetchCaptionText("https://example.com/api/timedtext?v=abcdefghijk")
            }
            assertFailsWith<IllegalArgumentException> {
                innerTube.fetchCaptionText("https://www.youtube.com:8443/api/timedtext?v=abcdefghijk")
            }
        }
    }

    @Test
    fun captionFetchRejectsOversizedBody() {
        runBlocking {
            val innerTube =
                InnerTube(
                    HttpClient(
                        MockEngine {
                            respond("x", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, (5 * 1024 * 1024).toString()))
                        },
                    ),
                )

            assertFailsWith<IllegalStateException> {
                innerTube.fetchCaptionText("https://www.youtube.com/api/timedtext?v=abcdefghijk")
            }
        }
    }

    @Test
    fun captionFetchDoesNotFollowRedirects() {
        runBlocking {
            var requests = 0
            val innerTube =
                InnerTube(
                    HttpClient(
                        MockEngine {
                            requests++
                            respond(
                                "",
                                HttpStatusCode.Found,
                                headersOf(HttpHeaders.Location, "https://example.com/api/timedtext"),
                            )
                        },
                    ),
                )

            assertFailsWith<InnerTubeHttpException> {
                innerTube.fetchCaptionText("https://www.youtube.com/api/timedtext?v=abcdefghijk")
            }
            assertEquals(1, requests)
        }
    }
}
