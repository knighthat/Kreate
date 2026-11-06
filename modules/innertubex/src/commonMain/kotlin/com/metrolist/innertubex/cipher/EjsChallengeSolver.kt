package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.d
import com.metrolist.innertubex.utils.sha1
import com.metrolist.innertubex.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock

private val PREPROCESSED_CACHE_KEY_REGEX = Regex("[a-f0-9]{40}")

/**
 * Runs yt-dlp's embedded JS solver (EJS) inside QuickJS — needed when the player uses
 * VM/table-driven ciphers that regex-based extraction cannot handle.
 *
 * @see <a href="https://github.com/yt-dlp/ejs">yt-dlp/ejs</a>
 */
internal class EjsChallengeSolver(
    private val engine: QuickJsEngine,
    private val logger: InnerTubeLogger,
    private val idleTimeoutMs: Long = 20_000L,
) {
    companion object {
        private const val TAG = "EjsChallengeSolver"
        private const val MAX_PREPROCESSED_PLAYERS = 1
        private const val MAX_SOLVED_CHALLENGES = 256
        private const val MAX_CACHED_CHALLENGE_LENGTH = 4096

        // Each compiled player keeps its whole closure alive inside the QuickJS heap.
        private const val MAX_COMPILED_PLAYERS = 2
        private const val MAX_PLAYER_JS_LENGTH = 8 * 1024 * 1024
        private const val MAX_CHALLENGE_LENGTH = 64 * 1024
        private const val MAX_CHALLENGES = 256
        private const val MAX_CHALLENGE_PAYLOAD_LENGTH = 8 * 1024 * 1024
        private const val MAX_PAYLOAD_LENGTH = 10 * 1024 * 1024
        private const val MAX_RAW_OUTPUT_LENGTH = 10 * 1024 * 1024
        private const val MAX_SOLVER_OUTPUT_LENGTH = 256 * 1024
        private val payloadJson = Json { encodeDefaults = false }
    }

    data class SolveResult(
        val sigByChallenge: Map<String, String>,
        val nByChallenge: Map<String, String>,
        val preprocessedPlayer: String?,
    )

    // Serialize whole solves, including bootstrap and cache I/O, against idle disposal.
    private val operationMutex = Mutex()
    private val idleScope = CoroutineScope(Dispatchers.Default)
    private var idleJob: Job? = null

    private data class ChallengeKey(
        val playerUrl: String,
        val kind: String,
        val challenge: String,
    )

    private val solvedChallenges = LinkedHashMap<ChallengeKey, String>()
    private var hasPersistentCache = false
    private var bootstrapped = false
    private var bundleHash: String? = null

    private val preprocessedByPlayerUrl = LinkedHashMap<String, String>()

    /**
     * Player URLs whose n/sig functions are probably compiled in `__itxPlayers`. Only a hint for trying
     * the compiled path: the JS map enforces its own bound, so lost bookkeeping cannot leak closures.
     */
    private val compiledPlayers = LinkedHashSet<String>()
    private var readPreprocessedPlayer: suspend (String) -> String? = { null }
    private var writePreprocessedPlayer: suspend (String, String?) -> Unit = { _, _ -> }

    /** Prewarms the runtime for the next burst of solves. */
    suspend fun ensureLoaded() = withActivity { ensureBootstrapped() }

    private suspend fun <T> withActivity(block: suspend () -> T): T =
        operationMutex.withLock {
            idleJob?.cancel()
            try {
                block()
            } finally {
                scheduleIdleRelease()
            }
        }

    private fun scheduleIdleRelease() {
        // This job captures only the solver, never a solve's multi-MB input or result.
        idleJob =
            idleScope.launch {
                delay(idleTimeoutMs)
                operationMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    releaseRuntime()
                    idleJob = null
                }
            }
    }

    private suspend fun releaseRuntime() {
        bootstrapped = false
        compiledPlayers.clear()
        preprocessedByPlayerUrl.clear()
        engine.dispose()
    }

    suspend fun dispose() =
        withContext(NonCancellable) {
            operationMutex.withLock {
                idleJob?.cancel()
                idleJob = null
                solvedChallenges.clear()
                releaseRuntime()
            }
        }

    suspend fun setPreprocessedPlayerCache(
        read: suspend (String) -> String?,
        write: suspend (String, String?) -> Unit,
    ) = operationMutex.withLock {
        readPreprocessedPlayer = read
        writePreprocessedPlayer = write
        hasPersistentCache = true
        preprocessedByPlayerUrl.clear()
    }

    suspend fun cachePreprocessedPlayer(
        playerUrl: String,
        preprocessedPlayer: String,
    ) {
        if (preprocessedPlayer.isBlank() || preprocessedPlayer.length > MAX_PLAYER_JS_LENGTH) return
        withActivity {
            if (hasPersistentCache) {
                ensureBundleHash()
                persistPreprocessedPlayer(bundleHash?.let { preprocessedPlayerCacheKey(playerUrl, it) }, preprocessedPlayer)
            }
            // Keep one supplied player until its next solve, even if persistent storage fails.
            preprocessedByPlayerUrl.clear()
            preprocessedByPlayerUrl[playerUrl] = preprocessedPlayer
        }
    }

    private suspend fun ensureBootstrapped() {
        if (bootstrapped) return
        try {
            val startMs = Clock.System.now().toEpochMilliseconds()
            engine.initialize()
            engine.setupYoutubeGlobals()
            val lib = readYtEjsSolverScript("yt.solver.lib.min.js")
            val core = readYtEjsSolverScript("yt.solver.core.min.js")
            bundleHash = sha1(lib + core)
            engine.execute(lib)
            engine.execute("Object.assign(globalThis, lib);")
            engine.execute(core)
            // Mirrors the final step of jsc() for preprocessed players, but keeps the compiled
            // functions so later challenges for the same player skip recompiling ~4 MB of JS.
            engine.execute(
                """
                globalThis.__itxPlayers = new Map();
                globalThis.__itxCompile = function(key, code) {
                  var fns = {"n":null,"sig":null};
                  Function("_result", code)(fns);
                  __itxPlayers.delete(key);
                  __itxPlayers.set(key, fns);
                  while (__itxPlayers.size > $MAX_COMPILED_PLAYERS) __itxPlayers.delete(__itxPlayers.keys().next().value);
                  return fns;
                };
                globalThis.__itxRun = function(fns, requests) {
                  return {"type":"result","responses":requests.map(function(req) {
                    if (req.type !== "n" && req.type !== "sig")
                      return {"type":"error","error":"Unknown request type: " + req.type};
                    var fn = fns[req.type];
                    if (!fn) return {"type":"error","error":"Failed to extract " + req.type + " function"};
                    try {
                      return {"type":"result","data":Object.fromEntries(req.challenges.map(function(c) { return [c, fn(c)]; }))};
                    } catch (e) {
                      return {"type":"error","error":String(e)};
                    }
                  })};
                };
                """.trimIndent(),
            )
            bootstrapped = true
            logger.d(TAG, "EJS bootstrap done elapsed=${Clock.System.now().toEpochMilliseconds() - startMs}ms")
        } catch (e: Throwable) {
            releaseRuntime()
            throw e
        }
    }

    private fun ensureBundleHash() {
        if (bundleHash == null) {
            bundleHash = sha1(readYtEjsSolverScript("yt.solver.lib.min.js") + readYtEjsSolverScript("yt.solver.core.min.js"))
        }
    }

    /**
     * @param requestOrder pairs of ("sig"|"n") to distinct challenge strings for that request.
     */
    suspend fun solve(
        playerUrl: String,
        fullPlayerJs: String,
        requestOrder: List<Pair<String, List<String>>>,
        preferPreprocessed: Boolean = true,
    ): SolveResult = solve(playerUrl, { fullPlayerJs }, requestOrder, preferPreprocessed)

    /** [loadFullPlayerJs] is only called when no usable preprocessed player is cached. */
    suspend fun solve(
        playerUrl: String,
        loadFullPlayerJs: suspend () -> String?,
        requestOrder: List<Pair<String, List<String>>>,
        preferPreprocessed: Boolean = true,
    ): SolveResult {
        if (requestOrder.isEmpty() || requestOrder.all { it.second.isEmpty() }) {
            return SolveResult(emptyMap(), emptyMap(), null)
        }
        if (requestOrder.sumOf { it.second.size } > MAX_CHALLENGES ||
            requestOrder.sumOf { (_, challenges) -> challenges.sumOf { it.length.toLong() } } >
            MAX_CHALLENGE_PAYLOAD_LENGTH ||
            requestOrder.any { (_, challenges) -> challenges.any { it.length > MAX_CHALLENGE_LENGTH } }
        ) {
            return SolveResult(emptyMap(), emptyMap(), null)
        }

        return withActivity {
            val cached = cachedChallenges(playerUrl, requestOrder)
            if (preferPreprocessed && cached.solvesAll(requestOrder)) {
                cached
            } else {
                solveUncached(playerUrl, loadFullPlayerJs, requestOrder, preferPreprocessed).also { result ->
                    for ((kind, values) in listOf("sig" to result.sigByChallenge, "n" to result.nByChallenge)) {
                        for ((challenge, value) in values) {
                            if (playerUrl.length + challenge.length + value.length > MAX_CACHED_CHALLENGE_LENGTH) continue
                            val key = ChallengeKey(playerUrl, kind, challenge)
                            solvedChallenges.remove(key)
                            solvedChallenges[key] = value
                            while (solvedChallenges.size > MAX_SOLVED_CHALLENGES) solvedChallenges.remove(solvedChallenges.keys.first())
                        }
                    }
                }
            }
        }
    }

    private fun cachedChallenges(
        playerUrl: String,
        requestOrder: List<Pair<String, List<String>>>,
    ): SolveResult {
        val sig = mutableMapOf<String, String>()
        val n = mutableMapOf<String, String>()
        for ((kind, challenges) in requestOrder) {
            for (challenge in challenges) {
                val key = ChallengeKey(playerUrl, kind, challenge)
                val value = solvedChallenges.remove(key) ?: continue
                solvedChallenges[key] = value
                when (kind) {
                    "sig" -> sig[challenge] = value
                    "n" -> n[challenge] = value
                }
            }
        }
        return SolveResult(sig, n, null)
    }

    private suspend fun solveUncached(
        playerUrl: String,
        loadFullPlayerJs: suspend () -> String?,
        requestOrder: List<Pair<String, List<String>>>,
        preferPreprocessed: Boolean,
    ): SolveResult {
        return try {
            ensureBootstrapped()
            if (preferPreprocessed && playerUrl in compiledPlayers) {
                val compiled =
                    try {
                        solveOnce(playerUrl, null, requestOrder, preprocessed = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        SolveResult(emptyMap(), emptyMap(), null)
                    }
                if (compiled.solvesAll(requestOrder)) return compiled
                forgetCompiledPlayer(playerUrl)
            }

            val cacheKey = bundleHash?.let { preprocessedPlayerCacheKey(playerUrl, it) }
            val preprocessed =
                if (preferPreprocessed) {
                    loadPreprocessedPlayer(playerUrl, cacheKey)
                } else {
                    null
                }

            var cachedResult = SolveResult(emptyMap(), emptyMap(), null)
            if (preprocessed != null) {
                cachedResult =
                    try {
                        solveOnce(playerUrl, preprocessed, requestOrder, preprocessed = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        SolveResult(emptyMap(), emptyMap(), null)
                    }
                if (cachedResult.solvesAll(requestOrder)) return cachedResult
                evictPreprocessedPlayer(playerUrl, cacheKey)
            }

            val fullPlayerJs =
                loadFullPlayerJs()?.takeIf { it.isNotBlank() && it.length <= MAX_PLAYER_JS_LENGTH } ?: return cachedResult
            solveOnce(playerUrl, fullPlayerJs, requestOrder, preprocessed = false).also { result ->
                result.preprocessedPlayer?.let { persistPreprocessedPlayer(cacheKey, it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, "EJS solve failed player=${playerUrl.logId()} type=${e.logType()}")
            SolveResult(emptyMap(), emptyMap(), null)
        }
    }

    /**
     * Solves with the full player ([preprocessed] false), a preprocessed player that is compiled and
     * kept for later calls, or, when [playerInputText] is null, the already compiled player.
     */
    private suspend fun solveOnce(
        playerUrl: String,
        playerInputText: String?,
        requestOrder: List<Pair<String, List<String>>>,
        preprocessed: Boolean,
    ): SolveResult {
        val requests =
            buildJsonArray {
                for ((kind, challenges) in requestOrder) {
                    if (challenges.isEmpty()) continue
                    add(
                        buildJsonObject {
                            put("type", kind)
                            putJsonArray("challenges") {
                                for (c in challenges) {
                                    add(JsonPrimitive(c))
                                }
                            }
                        },
                    )
                }
            }
        val requestsJson = payloadJson.encodeToString(JsonElement.serializer(), requests)
        val playerKey = QuickJsEngine.jsStringLiteral(playerUrl)
        val playerInput = playerInputText?.let(QuickJsEngine::jsStringLiteral).orEmpty()
        if (playerInput.length + requestsJson.length > MAX_PAYLOAD_LENGTH) {
            return SolveResult(emptyMap(), emptyMap(), null)
        }
        val run =
            when {
                playerInputText == null -> {
                    """
                    var fns = __itxPlayers.get($playerKey);
                    var r = fns ? __itxRun(fns, $requestsJson) : null;
                    """
                }

                preprocessed -> {
                    """
                    var r = __itxRun(__itxCompile($playerKey, $playerInput), $requestsJson);
                    """
                }

                else -> {
                    // Preprocessing a full player needs nearly the whole QuickJS heap.
                    """
                    __itxPlayers.clear();
                    var r = jsc({"type":"player","player":$playerInput,"output_preprocessed":true,"requests":$requestsJson});
                    """
                }
            }
        // jsc() may return objects QuickJS JSON.stringify cannot handle (circular refs);
        // copy only plain string fields into a new tree before stringify.
        val js =
            """
            (function() {
              $run
              if (!r) return JSON.stringify({"type":"error","error":"jsc returned null"});
              if (r.type === "error")
                return JSON.stringify({"type":"error","error":String(r.error != null ? r.error : "")});
              if (r.type !== "result")
                return JSON.stringify({"type":"error","error":"unexpected jsc type: "+String(r.type)});
              var out = {"type":"result","responses":[]};
              if (typeof r.preprocessed_player === "string" && r.preprocessed_player.length > 0) {
                if (r.preprocessed_player.length > $MAX_PLAYER_JS_LENGTH)
                  return JSON.stringify({"type":"error","error":"preprocessed player too large"});
                out.preprocessed_player = r.preprocessed_player;
              }
              var resps = r.responses;
              if (!resps) return JSON.stringify(out);
              for (var i = 0; i < resps.length; i++) {
                var resp = resps[i];
                if (!resp) {
                  out.responses.push({"type":"error","error":"null response"});
                  continue;
                }
                if (resp.type === "error") {
                  out.responses.push({
                    "type":"error",
                    "error":String(resp.error != null ? resp.error : "")
                  });
                } else if (resp.type === "result") {
                  var d = resp.data;
                  var plain = {};
                  if (d && typeof d === "object") {
                    var keys = Object.keys(d);
                    for (var j = 0; j < keys.length; j++) {
                      var k = keys[j];
                      var v = d[k];
                      var text = v == null ? "" : String(v);
                      if (text.length > $MAX_SOLVER_OUTPUT_LENGTH) {
                        plain = null;
                        break;
                      }
                      plain[k] = text;
                    }
                  }
                  if (plain == null)
                    out.responses.push({"type":"error","error":"solver output too large"});
                  else
                    out.responses.push({"type":"result","data":plain});
                } else {
                  out.responses.push({"type":"error","error":"unknown response type"});
                }
              }
              return JSON.stringify(out);
            })()
            """
        val evaluateStartMs = Clock.System.now().toEpochMilliseconds()
        when {
            playerInputText == null -> Unit
            preprocessed -> rememberCompiledPlayer(playerUrl)
            else -> compiledPlayers.clear()
        }
        val evaluated = engine.evaluate(js, MAX_RAW_OUTPUT_LENGTH, collectGarbage = !preprocessed)
        if (evaluated.length > MAX_RAW_OUTPUT_LENGTH) return SolveResult(emptyMap(), emptyMap(), null)
        val raw = evaluated.trim()
        logger.d(
            TAG,
            "EJS evaluate done preprocessed=$preprocessed requests=${requestOrder.sumOf {
                it.second.size
            }} elapsed=${Clock.System.now().toEpochMilliseconds() - evaluateStartMs}ms player=${playerUrl.logId()}",
        )
        return parseOutput(playerUrl, raw, requestOrder)
    }

    private suspend fun loadPreprocessedPlayer(
        playerUrl: String,
        cacheKey: String?,
    ): String? {
        preprocessedByPlayerUrl.remove(playerUrl)?.also {
            if (!hasPersistentCache) preprocessedByPlayerUrl[playerUrl] = it
            return it
        }
        cacheKey ?: return null
        val stored =
            try {
                readPreprocessedPlayer(cacheKey)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return null
        if (stored.isBlank() || stored.length > MAX_PLAYER_JS_LENGTH) {
            safeWritePreprocessedPlayer(cacheKey, null)
            return null
        }
        putPreprocessedPlayerLocked(playerUrl, stored)
        return stored
    }

    private suspend fun persistPreprocessedPlayer(
        cacheKey: String?,
        preprocessedPlayer: String,
    ) {
        if (cacheKey != null && preprocessedPlayer.isNotBlank() && preprocessedPlayer.length <= MAX_PLAYER_JS_LENGTH) {
            safeWritePreprocessedPlayer(cacheKey, preprocessedPlayer)
        }
    }

    private suspend fun evictPreprocessedPlayer(
        playerUrl: String,
        cacheKey: String?,
    ) {
        preprocessedByPlayerUrl.remove(playerUrl)
        forgetCompiledPlayer(playerUrl)
        cacheKey?.let { safeWritePreprocessedPlayer(it, null) }
    }

    /** Mirrors the LRU bound that `__itxCompile` applies to `__itxPlayers`. */
    private fun rememberCompiledPlayer(playerUrl: String) {
        compiledPlayers.remove(playerUrl)
        compiledPlayers.add(playerUrl)
        while (compiledPlayers.size > MAX_COMPILED_PLAYERS) compiledPlayers.remove(compiledPlayers.first())
    }

    private fun forgetCompiledPlayer(playerUrl: String) {
        compiledPlayers.remove(playerUrl)
    }

    private suspend fun safeWritePreprocessedPlayer(
        cacheKey: String,
        value: String?,
    ) {
        try {
            writePreprocessedPlayer(cacheKey, value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Cache failures must not break playback.
        }
    }

    private fun parseOutput(
        playerUrl: String,
        raw: String,
        requestOrder: List<Pair<String, List<String>>>,
    ): SolveResult {
        val root =
            try {
                Json.parseToJsonElement(raw).jsonObject
            } catch (e: Exception) {
                logger.w(TAG, "EJS output was not valid JSON (${e.logType()})")
                return SolveResult(emptyMap(), emptyMap(), null)
            }

        if (root["type"]?.jsonPrimitive?.content != "result") {
            logger.w(TAG, "EJS returned a top-level error")
            return SolveResult(emptyMap(), emptyMap(), null)
        }

        val preprocessed =
            root["preprocessed_player"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { it.length <= MAX_PLAYER_JS_LENGTH }
        if (preprocessed != null) {
            putPreprocessedPlayerLocked(playerUrl, preprocessed)
        }

        val responses = root["responses"]?.jsonArray ?: JsonArray(emptyList())
        val nonEmptyRequests = requestOrder.filter { it.second.isNotEmpty() }
        if (responses.size != nonEmptyRequests.size) {
            logger.w(
                TAG,
                "EJS responses size mismatch expected=${nonEmptyRequests.size} got=${responses.size}",
            )
        }

        val sigMap = mutableMapOf<String, String>()
        val nMap = mutableMapOf<String, String>()
        nonEmptyRequests.forEachIndexed { i, (kind, _) ->
            if (responses.size <= i) return@forEachIndexed
            val resp = responses[i].jsonObject
            when (resp["type"]?.jsonPrimitive?.content) {
                "result" -> {
                    val dataObj = resp["data"]?.jsonObject ?: return@forEachIndexed
                    val m =
                        dataObj.entries
                            .associate { entry ->
                                entry.key to
                                    entry.value.jsonPrimitive.content.takeIf {
                                        it.isNotBlank() && it.length <= MAX_SOLVER_OUTPUT_LENGTH
                                    }
                            }.filterValues { it != null }
                            .mapValues { it.value!! }
                    when (kind) {
                        "sig" -> sigMap.putAll(m)
                        "n" -> nMap.putAll(m)
                    }
                }

                else -> {
                    logger.w(TAG, "EJS request kind=$kind failed")
                }
            }
        }

        return SolveResult(sigMap, nMap, preprocessed)
    }

    private fun putPreprocessedPlayerLocked(
        playerUrl: String,
        preprocessedPlayer: String,
    ) {
        if (hasPersistentCache) return
        preprocessedByPlayerUrl.remove(playerUrl)
        preprocessedByPlayerUrl[playerUrl] = preprocessedPlayer
        while (preprocessedByPlayerUrl.size > MAX_PREPROCESSED_PLAYERS) {
            val oldestKey = preprocessedByPlayerUrl.keys.firstOrNull() ?: break
            preprocessedByPlayerUrl.remove(oldestKey)
        }
    }

    private fun String.logId(): String = RemotePlayerConfigParser.extractPlayerHash(this) ?: "unknown"

    private fun Throwable.logType(): String = this::class.simpleName ?: "Exception"
}

internal fun preprocessedPlayerCacheKey(
    playerUrl: String,
    bundleHash: String,
): String? {
    if (!PREPROCESSED_CACHE_KEY_REGEX.matches(bundleHash)) return null
    runCatching { validatedPlayerScriptUrl(playerUrl) }.getOrNull() ?: return null
    return sha1("$playerUrl:$bundleHash")
}

private fun EjsChallengeSolver.SolveResult.solvesAll(requestOrder: List<Pair<String, List<String>>>): Boolean =
    requestOrder.all { (kind, challenges) ->
        val solved =
            when (kind) {
                "sig" -> sigByChallenge
                "n" -> nByChallenge
                else -> return@all false
            }
        challenges.all(solved::containsKey)
    }
