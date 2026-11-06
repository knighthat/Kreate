package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.models.response.PlayerResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeCipherServiceTest {
    @Test
    fun rawPlayerCacheKeepsOnlyTheCurrentPlayer() =
        runBlocking {
            var downloads = 0
            val httpClient =
                HttpClient(
                    MockEngine {
                        downloads++
                        respond("player code", headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
                    },
                )
            val service = YouTubeCipherService(httpClient)
            try {
                val first = "https://www.youtube.com/s/player/12345678/player.js"
                val second = "https://www.youtube.com/s/player/87654321/player.js"
                service.initialize()
                assertEquals("player code", service.playerCode(first))
                assertEquals("player code", service.playerCode(first))
                assertEquals(1, downloads)
                service.playerCode(second)
                service.playerCode(first)
                assertEquals(3, downloads)
            } finally {
                service.dispose()
                httpClient.close()
            }
        }

    @Test
    fun directFormatsDoNotWaitForPlayerPreload() =
        runBlocking {
            val requestStarted = CompletableDeferred<Unit>()
            val service =
                YouTubeCipherService(
                    HttpClient(
                        MockEngine {
                            requestStarted.complete(Unit)
                            awaitCancellation()
                        },
                    ),
                )
            val preload =
                async {
                    service.preloadPlayerCode(
                        "https://www.youtube.com/s/player/12345678/player_ias.vflset/en_GB/base.js",
                    )
                }

            try {
                requestStarted.await()
                val format =
                    PlayerResponse.StreamingData.Format(
                        itag = 140,
                        url = "https://example.googlevideo.com/videoplayback?expire=1",
                        mimeType = "audio/mp4",
                    )

                val result = withTimeout(1_000) { service.processFormats("unused", listOf(format)) }

                assertEquals(listOf(format), result)
                assertFalse(preload.isCompleted)
            } finally {
                preload.cancelAndJoin()
            }
        }

    @Test
    fun processFormatsRejectsNFormatsWhenPlayerScriptUnavailable() =
        runBlocking {
            val httpClient =
                HttpClient(
                    MockEngine {
                        respond(
                            content = "not found",
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                        )
                    },
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val service = YouTubeCipherService(httpClient)
            val format =
                PlayerResponse.StreamingData.Format(
                    itag = 140,
                    url = "https://rr1---sn-test.c.youtube.com/videoplayback?expire=1&n=Qabc12XYZ&signature=abc",
                    mimeType = "audio/mp4",
                )

            val result =
                service.processFormats(
                    playerUrl = "https://www.youtube.com/s/player/66a6ea83/player_ias.vflset/en_GB/base.js",
                    formats = listOf(format),
                )

            assertEquals(1, result.size)
            assertNull(result.first().url)
        }

    @Test
    fun processFormatsDoesNotFetchPlayerScriptsFromUntrustedHosts() =
        runBlocking {
            val engine = MockEngine { error("Untrusted player URL must not be requested") }
            val service = YouTubeCipherService(HttpClient(engine))
            val format =
                PlayerResponse.StreamingData.Format(
                    itag = 140,
                    url = "https://example.googlevideo.com/videoplayback?n=challenge",
                    mimeType = "audio/mp4",
                )

            val result = service.processFormats("https://example.com/s/player/hash/base.js", listOf(format))

            assertNull(result.single().url)
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun cipherQueryValuesAreEncodedWithoutMovingFragments() {
        val service = YouTubeCipherService(HttpClient(MockEngine { error("No request expected") }))

        assertEquals(
            "https://example.googlevideo.com/videoplayback?expire=1&sig=a%26b%2Bc%23d#fragment",
            service.appendQueryParameter(
                "https://example.googlevideo.com/videoplayback?expire=1#fragment",
                "sig",
                "a&b+c#d",
            ),
        )
        assertNull(service.appendQueryParameter("https://example.test/video", "sig&other", "value"))
        assertNull(service.appendQueryParameter("https://example.test/video", "sig", ""))
        assertNull(service.appendQueryParameter("https://example.test/videoplayback", "sig", "value"))

        val url = "https://example.googlevideo.com/videoplayback?n=old&x=1#fragment"
        val match = Regex("[&?]n=([^&#]+)").find(url)!!
        assertEquals(
            "https://example.googlevideo.com/videoplayback?n=a%26b%2Bc&x=1#fragment",
            service.replaceQueryParameter(url, match, "a&b+c"),
        )

        val fragmentUrl = "https://example.googlevideo.com/videoplayback?x=1#fragment&n=old"
        val fragmentMatch = Regex("[&?]n=([^&#]+)").find(fragmentUrl)!!
        assertNull(service.replaceQueryParameter(fragmentUrl, fragmentMatch, "new"))
    }
}
