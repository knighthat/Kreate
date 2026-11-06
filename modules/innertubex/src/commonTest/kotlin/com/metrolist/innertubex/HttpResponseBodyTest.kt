package com.metrolist.innertubex

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpResponseBodyTest {
    @Test
    fun boundedBodyReaderRejectsOversizedDeclaredLength() =
        runBlocking {
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = "x".repeat(1024),
                            headers = headersOf(HttpHeaders.ContentLength, "1024"),
                        )
                    },
                )

            val error =
                assertFailsWith<IllegalStateException> {
                    client.get("https://example.test").bodyAsTextLimited(maxBytes = 8)
                }

            assertEquals("Response exceeded the 8 byte limit", error.message)
            client.close()
        }

    @Test
    fun boundedBodyReaderRejectsOversizedChunkedBody() =
        runBlocking {
            val client =
                HttpClient(
                    MockEngine {
                        respond(ByteReadChannel("x".repeat(1025)))
                    },
                )

            assertFailsWith<IllegalStateException> {
                client.get("https://example.test").bodyAsTextLimited(maxBytes = 1024)
            }
            client.close()
        }
}
