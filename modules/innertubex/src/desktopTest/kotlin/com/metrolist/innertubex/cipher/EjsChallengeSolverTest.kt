package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.utils.sha1
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EjsChallengeSolverTest {
    @Test
    fun persistsAndRestoresPreprocessedPlayerAcrossSolvers() =
        runBlocking {
            val stored = mutableMapOf<String, String>()
            var reads = 0
            val read: suspend (String) -> String? = { key ->
                reads += 1
                stored[key]
            }
            val write: suspend (String, String?) -> Unit = { key, value ->
                if (value == null) stored.remove(key) else stored[key] = value
            }

            val firstEngine = QuickJsEngine()
            try {
                val firstSolver = EjsChallengeSolver(firstEngine, InnerTubeLogger.NONE)
                firstSolver.setPreprocessedPlayerCache(read, write)
                val challenge = "private_challenge_not_for_persistent_storage"
                val first = firstSolver.solve(PLAYER_URL, PLAYER_CODE, listOf("sig" to listOf(challenge)))
                val memoryHit = firstSolver.solve(PLAYER_URL, "", listOf("n" to listOf("value")))

                assertEquals(challenge.reversed(), first.sigByChallenge[challenge])
                assertNotNull(first.preprocessedPlayer)
                assertFalse(stored.values.single().contains(challenge))
                assertFalse(stored.values.single().contains(challenge.reversed()))
                assertEquals("value_done", memoryHit.nByChallenge["value"])
                assertEquals(2, reads)
            } finally {
                firstEngine.dispose()
            }

            val secondEngine = QuickJsEngine()
            try {
                val secondSolver = EjsChallengeSolver(secondEngine, InnerTubeLogger.NONE)
                secondSolver.setPreprocessedPlayerCache(read, write)
                var fullPlayerLoads = 0
                val restored =
                    secondSolver.solve(
                        PLAYER_URL,
                        loadFullPlayerJs = {
                            fullPlayerLoads++
                            PLAYER_CODE
                        },
                        requestOrder = listOf("n" to listOf("fresh")),
                    )

                assertEquals("fresh_done", restored.nByChallenge["fresh"])
                assertEquals(0, fullPlayerLoads)
                assertEquals(3, reads)
                assertTrue(stored.keys.single().matches(Regex("[a-f0-9]{40}")))
            } finally {
                secondEngine.dispose()
            }
        }

    @Test
    fun reusesCompiledPreprocessedPlayerUntilAFullPlayerIsPreprocessed() =
        runBlocking {
            val engine = QuickJsEngine()
            try {
                val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE)
                val counted =
                    PLAYER_CODE.replace(
                        "(function() {\n",
                        "(function() {\nglobalThis.bodyRuns = (globalThis.bodyRuns || 0) + 1;\n",
                    )

                suspend fun bodyRuns() = engine.evaluate("globalThis.bodyRuns", maxResultLength = 8)

                val preprocessed = solver.solve(PLAYER_URL, counted, listOf("sig" to listOf("abc"))).preprocessedPlayer
                assertEquals("1", bodyRuns())
                repeat(3) { i ->
                    assertEquals("x${i}_done", solver.solve(PLAYER_URL, "", listOf("n" to listOf("x$i"))).nByChallenge["x$i"])
                }
                assertEquals("2", bodyRuns())

                // A full-player solve releases compiled players, so the next cached solve compiles again.
                solver.solve(OTHER_PLAYER_URL, counted, listOf("sig" to listOf("def")))
                solver.cachePreprocessedPlayer(PLAYER_URL, assertNotNull(preprocessed))
                assertEquals("ihg", solver.solve(PLAYER_URL, "", listOf("sig" to listOf("ghi"))).sigByChallenge["ghi"])
                assertEquals("4", bodyRuns())

                // The JS map keeps its own bound even when Kotlin bookkeeping is lost.
                for (hash in listOf("aaaaaaaa", "bbbbbbbb", "cccccccc")) {
                    solver.cachePreprocessedPlayer(PLAYER_URL.replace("12345678", hash), assertNotNull(preprocessed))
                    solver.solve(PLAYER_URL.replace("12345678", hash), "", listOf("n" to listOf("y")))
                }
                assertEquals("2", engine.evaluate("__itxPlayers.size", maxResultLength = 8))
            } finally {
                engine.dispose()
            }
        }

    @Test
    fun cacheKeysIsolatePlayerAndSolverVersions() {
        val bundleA = sha1("solver-a")
        val bundleB = sha1("solver-b")
        val key = assertNotNull(preprocessedPlayerCacheKey(PLAYER_URL, bundleA))

        assertTrue(key.matches(Regex("[a-f0-9]{40}")))
        assertFalse(key.contains("youtube"))
        assertNotEquals(key, preprocessedPlayerCacheKey(OTHER_PLAYER_URL, bundleA))
        assertNotEquals(key, preprocessedPlayerCacheKey(PLAYER_URL, bundleB))
        assertNull(preprocessedPlayerCacheKey("https://example.com/player.js", bundleA))
    }

    @Test
    fun badStoredPlayersAreDeletedBeforeFullPlayerFallback() =
        runBlocking {
            for (badValue in listOf("not javascript", "x".repeat(8 * 1024 * 1024 + 1))) {
                val engine = QuickJsEngine()
                val writes = mutableListOf<String?>()
                try {
                    val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE)
                    solver.setPreprocessedPlayerCache(
                        read = { badValue },
                        write = { _, value -> writes += value },
                    )

                    val result = solver.solve(PLAYER_URL, PLAYER_CODE, listOf("sig" to listOf("abc")))

                    assertEquals("cba", result.sigByChallenge["abc"])
                    assertNull(writes.first())
                    assertNotNull(writes.last())
                } finally {
                    engine.dispose()
                }
            }
        }

    @Test
    fun cacheCallbackFailuresDoNotBreakFullPlayerSolving() =
        runBlocking {
            val engine = QuickJsEngine()
            try {
                val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE)
                solver.setPreprocessedPlayerCache(
                    read = { error("read failed") },
                    write = { _, _ -> error("write failed") },
                )

                val result = solver.solve(PLAYER_URL, PLAYER_CODE, listOf("sig" to listOf("abc")))

                assertEquals("cba", result.sigByChallenge["abc"])
            } finally {
                engine.dispose()
            }
        }

    @Test
    fun solvesLargeWhitespacePlayerPayload() =
        runBlocking {
            val engine = QuickJsEngine()
            try {
                val challenge = "synthetic-large-challenge"
                val playerCode =
                    buildString(LARGE_PLAYER_JS_LENGTH) {
                        append(PLAYER_CODE)
                        append('\n')
                        while (length < LARGE_PLAYER_JS_LENGTH) append(' ')
                    }

                val result =
                    EjsChallengeSolver(engine, InnerTubeLogger.NONE).solve(
                        PLAYER_URL,
                        playerCode,
                        listOf("sig" to listOf(challenge)),
                    )

                assertEquals(challenge.reversed(), result.sigByChallenge[challenge])
            } finally {
                engine.dispose()
            }
        }

    @Test
    fun oversizedChallengesAreRejectedBeforeInitializingQuickJs() =
        runBlocking {
            val solver = EjsChallengeSolver(QuickJsEngine(), InnerTubeLogger.NONE)

            val result =
                solver.solve(
                    playerUrl = "https://www.youtube.com/s/player/12345678/player.js",
                    fullPlayerJs = "var player = true;",
                    requestOrder = listOf("sig" to listOf("x".repeat(64 * 1024 + 1))),
                )

            assertTrue(result.sigByChallenge.isEmpty())
            assertTrue(result.nByChallenge.isEmpty())
            assertTrue(result.preprocessedPlayer == null)
        }

    @Test
    fun idleReleaseStopsTheThreadAndCachedOutputsSurviveWithoutRestartingIt() =
        runBlocking {
            val engine = QuickJsEngine()
            // Slow CI workers can idle-release a 100ms runtime before the thread count is sampled.
            val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE, idleTimeoutMs = 1_000)
            val threadsBefore = quickJsThreads()
            val stored = mutableMapOf<String, String>()
            var reads = 0
            solver.setPreprocessedPlayerCache(
                read = {
                    reads++
                    stored[it]
                },
                write = { key, value -> if (value == null) stored.remove(key) else stored[key] = value },
            )
            try {
                val requests = listOf("sig" to listOf("abc"), "n" to listOf("abc"))
                val first = solver.solve(PLAYER_URL, PLAYER_CODE, requests)
                assertEquals("cba", first.sigByChallenge["abc"])
                assertEquals("abc_done", first.nByChallenge["abc"])
                val createdThreads = quickJsThreads() - threadsBefore
                assertEquals(1, createdThreads.size)
                awaitReleased(engine)
                withTimeout(5_000) { while (createdThreads.any { it.isAlive }) delay(10) }

                val cached = solver.solve(PLAYER_URL, { error("Cached results must not load player JS") }, requests)
                assertEquals(first.sigByChallenge, cached.sigByChallenge)
                assertEquals(first.nByChallenge, cached.nByChallenge)
                assertNull(cached.preprocessedPlayer)
                assertFailsWith<IllegalStateException> { engine.evaluate("1", 1) }

                val next = solver.solve(PLAYER_URL, { error("Player should be restored from disk") }, listOf("n" to listOf("new")))
                assertEquals("new_done", next.nByChallenge["new"])
                assertEquals(2, reads)
                assertEquals("1", engine.evaluate("__itxPlayers.size", 8))
                // Same challenge in a different player must not reuse the old player's output.
                val other = solver.solve(OTHER_PLAYER_URL, PLAYER_CODE.replace("_done", "_other"), listOf("n" to listOf("abc")))
                assertEquals("abc_other", other.nByChallenge["abc"])
            } finally {
                solver.dispose()
            }
        }

    @Test
    fun activeAndCancelledSolvesCannotRaceIdleRelease() =
        runBlocking {
            val engine = QuickJsEngine()
            val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE, idleTimeoutMs = 100)
            val loading = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            try {
                solver.ensureLoaded()
                engine.execute("globalThis.burstMarker = 'warm';")
                val active =
                    async(Dispatchers.Default) {
                        solver.solve(PLAYER_URL, {
                            loading.complete(Unit)
                            resume.await()
                            PLAYER_CODE
                        }, listOf("sig" to listOf("abc")))
                    }
                loading.await()
                delay(250)
                assertEquals("warm", engine.evaluate("burstMarker", 8))
                resume.complete(Unit)
                assertEquals("cba", active.await().sigByChallenge["abc"])
                val burst =
                    List(8) { i ->
                        async(Dispatchers.Default) {
                            solver.solve(PLAYER_URL, "", listOf("n" to listOf("burst$i")))
                        }
                    }.awaitAll()
                burst.forEachIndexed { i, result -> assertEquals("burst${i}_done", result.nByChallenge["burst$i"]) }
                assertEquals("warm", engine.evaluate("burstMarker", 8))

                val cancelling = CompletableDeferred<Unit>()
                val cancelled =
                    async(Dispatchers.Default) {
                        solver.solve(OTHER_PLAYER_URL, {
                            cancelling.complete(Unit)
                            awaitCancellation()
                        }, listOf("n" to listOf("cancelled")))
                    }
                cancelling.await()
                cancelled.cancelAndJoin()
                awaitReleased(engine)
                assertEquals("zyx", solver.solve(PLAYER_URL, PLAYER_CODE, listOf("sig" to listOf("xyz"))).sigByChallenge["xyz"])
            } finally {
                solver.dispose()
            }
        }

    @Test
    fun concurrentDuplicatesSolveOnceAndDisposeAllowsFreshBootstrap() =
        runBlocking {
            val engine = QuickJsEngine()
            val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE)
            var loads = 0
            try {
                val results =
                    List(12) {
                        async(Dispatchers.Default) {
                            solver.solve(PLAYER_URL, {
                                loads++
                                PLAYER_CODE
                            }, listOf("sig" to listOf("abc")))
                        }
                    }.awaitAll()
                assertTrue(results.all { it.sigByChallenge["abc"] == "cba" })
                assertEquals(1, loads)
                solver.dispose()
                assertFailsWith<IllegalStateException> { engine.evaluate("1", 1) }
                assertEquals("cba", solver.solve(PLAYER_URL, PLAYER_CODE, listOf("sig" to listOf("abc"))).sigByChallenge["abc"])
            } finally {
                solver.dispose()
            }
        }

    @Test
    fun solvedOutputCacheIsBoundedAndFailuresAreRetried() =
        runBlocking {
            val engine = QuickJsEngine()
            val solver = EjsChallengeSolver(engine, InnerTubeLogger.NONE, idleTimeoutMs = 100)
            try {
                assertTrue(solver.solve(PLAYER_URL, "invalid", listOf("n" to listOf("retry"))).nByChallenge.isEmpty())
                assertEquals("retry_done", solver.solve(PLAYER_URL, PLAYER_CODE, listOf("n" to listOf("retry"))).nByChallenge["retry"])
                val challenges = List(256) { "value$it" }
                assertEquals(256, solver.solve(PLAYER_URL, "", listOf("n" to challenges)).nByChallenge.size)
                awaitReleased(engine)
                // The oldest entry was evicted and must be solved again.
                var loads = 0
                assertEquals(
                    "retry_done",
                    solver
                        .solve(PLAYER_URL, {
                            loads++
                            PLAYER_CODE
                        }, listOf("n" to listOf("retry")))
                        .nByChallenge["retry"],
                )
                assertEquals(1, loads)
            } finally {
                solver.dispose()
            }
        }

    private suspend fun awaitReleased(engine: QuickJsEngine) =
        withTimeout(5_000) {
            while (true) {
                try {
                    engine.evaluate("1", 1)
                } catch (_: IllegalStateException) {
                    break
                }
                delay(10)
            }
        }

    private fun quickJsThreads(): Set<Thread> =
        Thread
            .getAllStackTraces()
            .keys
            .filter { it.name == "InnerTubeX-QuickJS" }
            .toSet()

    private companion object {
        const val LARGE_PLAYER_JS_LENGTH = 2 * 1024 * 1024
        const val PLAYER_URL = "https://www.youtube.com/s/player/12345678/player.js"
        const val OTHER_PLAYER_URL = "https://www.youtube.com/s/player/87654321/player.js"
        val PLAYER_CODE =
            """
            (function() {
              function PlayerUrl(url, key, signature) {
                this.values = {s: signature || null, n: null};
              }
              PlayerUrl.prototype.set = function(key, value) { this.values[key] = value; };
              PlayerUrl.prototype.get = function(key) { return this.values[key]; };
              PlayerUrl.prototype.clone = function() { return this; };
              PlayerUrl.prototype.apply = function() {
                if (this.values.s) this.values.s = this.values.s.split("").reverse().join("");
                if (this.values.n) this.values.n += "_done";
              };
              function transform(url, key, signature) {
                var value = new PlayerUrl(url, key, signature);
                value.set("alr", "yes");
                return value;
              }
            }).call(this);
            """.trimIndent()
    }
}
