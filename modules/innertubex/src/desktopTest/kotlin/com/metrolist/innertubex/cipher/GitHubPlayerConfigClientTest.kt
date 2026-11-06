package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.InnerTubeLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubPlayerConfigClientTest {
    @Test
    fun missingConfigIsNotRefetchedForEveryCipherPass() =
        runBlocking {
            val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
            val repository =
                object : PlayerConfigRepository {
                    override val enabled = true
                    override val defaultSourceUrl = ""
                    override val sourceUrl =
                        "https://cdn.jsdelivr.net/gh/MetrolistGroup/faraday@{playerTag}/registry/players/{playerHash}.json"
                    override var cachedJson = ""
                    override var cachedAtMs = 0L
                    override var cachedSourceUrl = ""
                    override var cachedEtag = ""
                }
            val engineJs = QuickJsEngine()
            try {
                val client =
                    GitHubPlayerConfigClient(
                        HttpClient(engine),
                        EjsChallengeSolver(engineJs, InnerTubeLogger.NONE),
                        repository,
                        { runCatching { Url(it) }.getOrNull() },
                        InnerTubeLogger.NONE,
                    )
                val playerUrl = "https://www.youtube.com/s/player/abcdef12/player_ias.vflset/en_GB/base.js"

                repeat(3) { client.solve(playerUrl, listOf("sig"), listOf("n")) }

                // One jsDelivr request plus its raw GitHub fallback, then both misses are cached.
                assertEquals(2, engine.requestHistory.size)
            } finally {
                engineJs.dispose()
            }
        }
}
