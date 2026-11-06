package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.d
import com.metrolist.innertubex.extraction.ExtractionCipherService
import com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format
import com.metrolist.innertubex.utils.decodeQueryComponent
import com.metrolist.innertubex.w
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Service for deobfuscating YouTube player cipher challenges.
 *
 * This service handles:
 * - N-parameter challenges (throttling prevention)
 * - Signature cipher challenges (URL decryption)
 *
 * Uses Faraday's validated zemer-style player configs first, then yt-dlp EJS
 * and regex-based [PlayerScriptParser] fallbacks.
 */
class YouTubeCipherService(
    private val httpClient: HttpClient,
    private val remotePlayerConfigStore: RemotePlayerConfigStore? = null,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) : ExtractionCipherService {
    private class PlayerScriptHttpException(
        status: Int,
        val retryable: Boolean,
    ) : IllegalStateException("Player script request failed with HTTP $status")

    private companion object {
        private const val TAG = "YouTubeCipherService"
        private const val OKHTTP_USER_AGENT = "okhttp/5.4.0"
        private const val MAX_PLAYER_CACHE_ENTRIES = 4
        private const val MAX_PLAYER_SCRIPT_BYTES = 8 * 1024 * 1024
        private const val MAX_MEDIA_URL_LENGTH = 16 * 1024
        private const val MAX_SOLVER_OUTPUT_LENGTH = 256 * 1024
        private val N_PARAMETER_REGEX = Regex("[&?]n=([^&#]+)")
        private val QUERY_PARAMETER_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9._~-]*")
    }

    private val engine = QuickJsEngine()
    private val ejs = EjsChallengeSolver(engine, logger)
    private val githubPreprocessedPlayer =
        remotePlayerConfigStore?.let { store ->
            GitHubPlayerConfigClient(httpClient, ejs, store.repository, store::validatedSourceUrlOrNull, logger)
        }
    private val cache = LinkedHashMap<String, CachedSolver>()
    private val zemerCache = LinkedHashMap<String, CachedZemerSolver>()
    private val playerCodeCache = LinkedHashMap<String, String>()
    private val playerCodeDownloads = mutableMapOf<String, CompletableDeferred<String>>()
    private val cacheMutex = Mutex()
    private val operationMutex = Mutex()

    private data class PlayerCodeResult(
        val code: String,
        val source: String,
    )

    private data class CachedSolver(
        val playerUrl: String,
        val engine: QuickJsEngine,
        val nSolver: suspend (String) -> String?,
        val sigSolver: suspend (String) -> String?,
    )

    private data class CachedZemerSolver(
        val config: RemotePlayerConfigParser.HardcodedPlayerConfig,
        val configEpoch: Long,
        val solver: ZemerCipherSolver,
    )

    private data class RemoteSolveResult(
        val sigByChallenge: Map<String, String>,
        val nByChallenge: Map<String, String>,
    )

    /**
     * Initialize the cipher service.
     */
    override suspend fun initialize() = Unit

    /**
     * Installs optional persistent storage for generated EJS preprocessed players.
     * Passing null to [write] deletes an unusable cached value.
     */
    suspend fun setPreprocessedPlayerCache(
        read: suspend (String) -> String?,
        write: suspend (String, String?) -> Unit,
    ) = operationMutex.withLock { ejs.setPreprocessedPlayerCache(read, write) }

    override suspend fun prewarmEjs() = operationMutex.withLock { ejs.ensureLoaded() }

    /**
     * Notify the remote config workflow that a deciphered stream was rejected by
     * the CDN. Zemer treats this as a distinct signal from an unknown player hash
     * because a stale solver can return a syntactically valid but wrong signature.
     */
    suspend fun refreshAfterStreamRejection(): Boolean = remotePlayerConfigStore?.refreshAfterStreamRejection() ?: false

    /** Force a single remote-config refresh when a rotated player hash is unknown. */
    suspend fun refreshForUnknownPlayer(playerUrl: String): Boolean {
        val hash = RemotePlayerConfigParser.extractPlayerHash(playerUrl) ?: return false
        return remotePlayerConfigStore?.forceRefresh(missingHash = hash) ?: false
    }

    /** Shares the bounded player-script cache with other components that need the raw player code. */
    internal suspend fun playerCode(playerUrl: String): String = getOrDownloadPlayerCode(canonicalPlayerUrl(playerUrl)).code

    override suspend fun preloadPlayerCode(playerUrl: String) = preloadCanonicalPlayerCode(canonicalPlayerUrl(playerUrl))

    private suspend fun preloadCanonicalPlayerCode(playerUrl: String) =
        operationMutex.withLock {
            val startMs = Clock.System.now().toEpochMilliseconds()
            try {
                // Remote configs solve from the raw player code; EJS only needs it when no preprocessed player is cached.
                if (remotePlayerConfigStore?.getConfig(playerUrl) != null) {
                    getOrDownloadPlayerCode(playerUrl)
                } else {
                    ejs.solve(playerUrl, { getOrDownloadPlayerCode(playerUrl).code }, listOf("sig" to listOf("prewarm")))
                }
                logger.d(
                    TAG,
                    "player JS preload done elapsed=${Clock.System.now().toEpochMilliseconds() - startMs}ms player=${playerUrl.logId()}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.d(
                    TAG,
                    "player JS preload failed elapsed=${Clock.System.now().toEpochMilliseconds() - startMs}ms player=${playerUrl.logId()} type=${e.logType()}",
                )
            }
        }

    /**
     * Process streaming data formats and deobfuscate cipher URLs.
     *
     * @param playerUrl The URL of the YouTube player JS
     * @param formats The streaming formats from the player response
     * @return List of formats with deobfuscated URLs
     */
    override suspend fun processFormats(
        playerUrl: String,
        formats: List<Format>,
    ): List<Format> = processCanonicalFormats(canonicalPlayerUrl(playerUrl), formats)

    private suspend fun processCanonicalFormats(
        playerUrl: String,
        formats: List<Format>,
    ): List<Format> {
        val directNValues = formats.map { it.url?.extractNParameter() }
        val cipherFormats = formats.count { !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank() }
        val nFormats = directNValues.count { it != null }
        if (cipherFormats == 0 && nFormats == 0) return formats
        return withContext(Dispatchers.Default) {
            operationMutex.withLock {
                val totalStartMs = Clock.System.now().toEpochMilliseconds()

                val cached = cacheMutex.withLock { solverCacheHitLocked(playerUrl) }
                logger.d(
                    TAG,
                    "processFormats start formats=${formats.size} cipherFormats=$cipherFormats nFormats=$nFormats cachedSolver=${cached != null} player=${playerUrl.logId()}",
                )

                val working = formats.toMutableList()
                var playerCodeResult: PlayerCodeResult? = null
                var playerCodeFailed: Throwable? = null

                suspend fun playerCode(): String? {
                    playerCodeResult?.let { return it.code }
                    playerCodeFailed?.let { return null }
                    return try {
                        val playerCodeStartMs = Clock.System.now().toEpochMilliseconds()
                        val result = getOrDownloadPlayerCode(playerUrl)
                        playerCodeResult = result
                        logger.d(
                            TAG,
                            "player JS ready source=${result.source} size=${result.code.length} elapsed=${Clock.System.now().toEpochMilliseconds() - playerCodeStartMs}ms player=${playerUrl.logId()}",
                        )
                        result.code
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        playerCodeFailed = e
                        logger.w(
                            TAG,
                            "player JS unavailable, challenges unsolvable player=${playerUrl.logId()} type=${e.logType()}",
                        )
                        null
                    }
                }

                // --- Signature ciphers (batch EJS, then parser fallback) ---
                data class SigTask(
                    val index: Int,
                    val params: Map<String, String>,
                    val useSignatureCipherField: Boolean,
                )

                data class NTask(
                    val index: Int,
                    val url: String,
                    val nValue: String,
                )

                val sigTasks = mutableListOf<SigTask>()
                for ((i, format) in working.withIndex()) {
                    val sc = format.signatureCipher
                    if (!sc.isNullOrBlank()) {
                        val p = parseCipherParams(sc)
                        if (p["s"] != null && p["url"]?.isApprovedMediaUrl() == true && p.hasValidSignatureParameter()) {
                            sigTasks.add(SigTask(i, p, useSignatureCipherField = true))
                        }
                        continue
                    }
                    val c = format.cipher
                    if (!c.isNullOrBlank()) {
                        val p = parseCipherParams(c)
                        if (p["s"] != null && p["url"]?.isApprovedMediaUrl() == true && p.hasValidSignatureParameter()) {
                            sigTasks.add(SigTask(i, p, useSignatureCipherField = false))
                        }
                    }
                }

                val sigChallenges = sigTasks.mapNotNull { it.params["s"] }.distinct()
                val nChallengesFromCipherUrls = sigTasks.mapNotNull { it.params["url"]?.extractNParameter() }.distinct()
                val directNChallenges = directNValues.filterNotNull().distinct()
                val initialNChallenges = (nChallengesFromCipherUrls + directNChallenges).distinct()
                var remoteNRequested = initialNChallenges.toSet()
                val zemerSolved =
                    if (sigChallenges.isNotEmpty() || initialNChallenges.isNotEmpty()) {
                        val faradayStartMs = Clock.System.now().toEpochMilliseconds()
                        solveWithZemerConfig(
                            playerUrl = playerUrl,
                            sigValues = sigChallenges,
                            nValues = initialNChallenges,
                        ).also { solved ->
                            if (solved.sigByChallenge.isNotEmpty() || solved.nByChallenge.isNotEmpty()) {
                                logger.d(
                                    TAG,
                                    "Faraday zemer config done sigRequested=${sigChallenges.size} sigSolved=${solved.sigByChallenge.size} nRequested=${initialNChallenges.size} nSolved=${solved.nByChallenge.size} elapsed=${Clock.System.now().toEpochMilliseconds() - faradayStartMs}ms player=${playerUrl.logId()}",
                                )
                            }
                        }
                    } else {
                        RemoteSolveResult(emptyMap(), emptyMap())
                    }

                val legacyRemoteSolved =
                    solveWithGitHubConfig(
                        playerUrl = playerUrl,
                        sigValues = sigChallenges.filterNot { it in zemerSolved.sigByChallenge },
                        nValues = initialNChallenges.filterNot { it in zemerSolved.nByChallenge },
                    )
                val remoteSolved =
                    RemoteSolveResult(
                        sigByChallenge = zemerSolved.sigByChallenge + legacyRemoteSolved.sigByChallenge,
                        nByChallenge = zemerSolved.nByChallenge + legacyRemoteSolved.nByChallenge,
                    )

                val solvedNFromSignaturePass = remoteSolved.nByChallenge.toMutableMap()
                val preferLocalPreprocessed = remoteSolved.sigByChallenge.isEmpty() && remoteSolved.nByChallenge.isEmpty()
                if (sigTasks.isNotEmpty()) {
                    val solvedSig = remoteSolved.sigByChallenge.toMutableMap()
                    val missingSigChallenges = sigChallenges.filterNot { it in solvedSig }
                    if (missingSigChallenges.isNotEmpty()) {
                        val sigStartMs = Clock.System.now().toEpochMilliseconds()
                        val ejsSig =
                            ejs.solve(
                                playerUrl = playerUrl,
                                loadFullPlayerJs = { playerCode() },
                                requestOrder =
                                    buildList {
                                        add("sig" to missingSigChallenges)
                                        initialNChallenges
                                            .filterNot { it in solvedNFromSignaturePass }
                                            .takeIf { it.isNotEmpty() }
                                            ?.let { add("n" to it) }
                                    },
                                preferPreprocessed = preferLocalPreprocessed,
                            )
                        solvedSig += ejsSig.sigByChallenge
                        solvedNFromSignaturePass += ejsSig.nByChallenge
                        logger.d(
                            TAG,
                            "signature EJS done tasks=${sigTasks.size} unique=${sigChallenges.size} missing=${missingSigChallenges.size} solved=${ejsSig.sigByChallenge.size} nRemote=${solvedNFromSignaturePass.size} elapsed=${Clock.System.now().toEpochMilliseconds() - sigStartMs}ms player=${playerUrl.logId()}",
                        )
                    } else {
                        logger.d(
                            TAG,
                            "signature remote player config reused tasks=${sigTasks.size} unique=${sigChallenges.size} solved=${solvedSig.size} player=${playerUrl.logId()}",
                        )
                    }

                    for (task in sigTasks) {
                        val s = task.params["s"] ?: continue
                        val solved = solvedSig[s]
                        if (solved != null) {
                            val url = task.params["url"] ?: continue
                            val sp = task.params["sp"] ?: "signature"
                            val newUrl = appendQueryParameter(url, sp, solved) ?: continue
                            val fmt = working[task.index]
                            working[task.index] =
                                if (task.useSignatureCipherField) {
                                    fmt.copy(url = newUrl, signatureCipher = null)
                                } else {
                                    fmt.copy(url = newUrl, cipher = null)
                                }
                        }
                    }

                    val failedSig = sigTasks.filter { it.params["s"] != null && solvedSig[it.params["s"]] == null }
                    if (failedSig.isNotEmpty()) {
                        val fallbackStartMs = Clock.System.now().toEpochMilliseconds()
                        val solver =
                            playerCode()?.let { code ->
                                getOrCreateSolver(playerUrl, code)
                            }
                        if (solver != null) {
                            for (task in failedSig) {
                                val fmt = working[task.index]
                                working[task.index] = processFormatWithSolver(solver, fmt)
                            }
                            logger.d(
                                TAG,
                                "signature fallback done failed=${failedSig.size} elapsed=${Clock.System.now().toEpochMilliseconds() - fallbackStartMs}ms player=${playerUrl.logId()}",
                            )
                        }
                    }
                }

                // --- n-parameter (batch EJS, then parser fallback) ---
                val nTasks = mutableListOf<NTask>()
                for ((i, format) in working.withIndex()) {
                    val url = format.url ?: continue
                    val nValue = url.extractNParameter() ?: continue
                    nTasks.add(NTask(i, url, nValue))
                }

                if (nTasks.isNotEmpty()) {
                    val nChallenges = nTasks.map { it.nValue }.distinct()
                    val solvedN = solvedNFromSignaturePass.toMutableMap()
                    val missingRemoteChallenges = nChallenges.filterNot { it in solvedN || it in remoteNRequested }
                    if (missingRemoteChallenges.isNotEmpty()) {
                        val faradayStartMs = Clock.System.now().toEpochMilliseconds()
                        val zemerSolvedN =
                            solveWithZemerConfig(
                                playerUrl = playerUrl,
                                sigValues = emptyList(),
                                nValues = missingRemoteChallenges,
                            ).nByChallenge
                        val legacySolvedN =
                            solveWithGitHubConfig(
                                playerUrl = playerUrl,
                                sigValues = emptyList(),
                                nValues = missingRemoteChallenges.filterNot { it in zemerSolvedN },
                            ).nByChallenge
                        val remoteSolvedN = zemerSolvedN + legacySolvedN
                        solvedN += remoteSolvedN
                        if (remoteSolvedN.isNotEmpty()) {
                            logger.d(
                                TAG,
                                "n remote config done tasks=${nTasks.size} requested=${missingRemoteChallenges.size} solved=${remoteSolvedN.size} elapsed=${Clock.System.now().toEpochMilliseconds() - faradayStartMs}ms player=${playerUrl.logId()}",
                            )
                        }
                    }

                    val missingNChallenges = nChallenges.filterNot { it in solvedN }
                    if (missingNChallenges.isNotEmpty()) {
                        val nStartMs = Clock.System.now().toEpochMilliseconds()
                        val ejsN =
                            ejs.solve(
                                playerUrl = playerUrl,
                                loadFullPlayerJs = { playerCode() },
                                requestOrder = listOf("n" to missingNChallenges),
                                preferPreprocessed = preferLocalPreprocessed,
                            )
                        solvedN += ejsN.nByChallenge
                        logger.d(
                            TAG,
                            "n EJS done tasks=${nTasks.size} unique=${nChallenges.size} missing=${missingNChallenges.size} solved=${ejsN.nByChallenge.size} reused=${solvedNFromSignaturePass.size} elapsed=${Clock.System.now().toEpochMilliseconds() - nStartMs}ms player=${playerUrl.logId()}",
                        )
                    } else {
                        logger.d(
                            TAG,
                            "n prior cipher pass reused tasks=${nTasks.size} unique=${nChallenges.size} solved=${solvedNFromSignaturePass.size} player=${playerUrl.logId()}",
                        )
                    }

                    for (task in nTasks) {
                        val solved = solvedN[task.nValue]
                        if (solved != null) {
                            val fmt = working[task.index]
                            val match = task.url.findNParameter() ?: continue
                            val newUrl = replaceQueryParameter(task.url, match, solved) ?: continue
                            working[task.index] = fmt.copy(url = newUrl)
                        }
                    }

                    val failedN = nTasks.filter { solvedN[it.nValue] == null }
                    if (failedN.isNotEmpty()) {
                        val fallbackStartMs = Clock.System.now().toEpochMilliseconds()
                        val solver =
                            playerCode()?.let { code ->
                                getOrCreateSolver(playerUrl, code)
                            }
                        if (solver != null) {
                            for (task in failedN) {
                                val fmt = working[task.index]
                                working[task.index] = processFormatWithSolver(solver, fmt)
                            }
                            logger.d(
                                TAG,
                                "n fallback done failed=${failedN.size} elapsed=${Clock.System.now().toEpochMilliseconds() - fallbackStartMs}ms player=${playerUrl.logId()}",
                            )
                        }
                    }

                    for (task in nTasks) {
                        val format = working[task.index]
                        val finalN = format.url?.extractNParameter()
                        if (finalN == null || finalN == task.nValue) {
                            working[task.index] = format.copy(url = null)
                            logger.w(
                                TAG,
                                "rejecting format with unresolved n itag=${format.itag} player=${playerUrl.logId()}",
                            )
                        }
                    }
                }

                // Logging (same as before)
                val processedFormats = working.toList()
                for ((i, format) in formats.withIndex()) {
                    val processed = processedFormats[i]
                    if (processed.width == null) {
                        val hadCipher =
                            !format.signatureCipher.isNullOrBlank() || !format.cipher.isNullOrBlank()
                        if (hadCipher && processed.url.isNullOrBlank()) {
                            logger.w(
                                TAG,
                                "audio format cipher deobfuscation produced no url itag=${format.itag} player=${playerUrl.logId()}",
                            )
                        }
                        if (!hadCipher && format.url.isNullOrBlank()) {
                            logger.w(
                                TAG,
                                "audio format has no url and no cipher fields (DRM/SABR-only?) itag=${format.itag} mime=${format.mimeType}",
                            )
                        }
                    }
                }

                logger.d(
                    TAG,
                    "processFormats done formats=${formats.size} usableAudio=${processedFormats.count {
                        !it.url.isNullOrBlank()
                    }} elapsed=${Clock.System.now().toEpochMilliseconds() - totalStartMs}ms player=${playerUrl.logId()}",
                )
                processedFormats
            }
        }
    }

    private suspend fun processFormatWithSolver(
        solver: CachedSolver,
        format: Format,
    ): Format {
        val signatureCipher = format.signatureCipher
        if (!signatureCipher.isNullOrBlank()) {
            val params = parseCipherParams(signatureCipher)
            val url = params["url"] ?: return format
            val signature = params["s"] ?: return format
            val solvedSig = solver.sigSolver(signature)
            if (solvedSig != null) {
                val sigParam = params["sp"] ?: "signature"
                val newUrl = appendQueryParameter(url, sigParam, solvedSig) ?: return format
                return format.copy(url = newUrl, signatureCipher = null)
            }
        }

        val cipher = format.cipher
        if (!cipher.isNullOrBlank()) {
            val params = parseCipherParams(cipher)
            val url = params["url"] ?: return format
            val signature = params["s"] ?: return format
            val solvedSig = solver.sigSolver(signature)
            if (solvedSig != null) {
                val sigParam = params["sp"] ?: "signature"
                val newUrl = appendQueryParameter(url, sigParam, solvedSig) ?: return format
                return format.copy(url = newUrl, cipher = null)
            }
        }

        val url = format.url
        if (!url.isNullOrBlank() && url.extractNParameter() != null) {
            val processedUrl = processNParameterWithSolver(solver, url)
            if (processedUrl != null) {
                return format.copy(url = processedUrl)
            }
        }

        return format
    }

    private suspend fun processNParameterWithSolver(
        solver: CachedSolver,
        url: String,
    ): String? {
        val nMatch = url.findNParameter() ?: return null
        val nValue = nMatch.groupValues[1]
        val solvedN = solver.nSolver(nValue) ?: return null
        return replaceQueryParameter(url, nMatch, solvedN)
    }

    /**
     * Deobfuscate a signature cipher string.
     *
     * @param playerUrl The URL of the YouTube player JS
     * @param cipher The cipher string from the format
     * @return The deobfuscated URL or null if failed
     */
    suspend fun deobfuscateSignatureCipher(
        playerUrl: String,
        cipher: String,
    ): String? = deobfuscateCanonicalSignatureCipher(canonicalPlayerUrl(playerUrl), cipher)

    private suspend fun deobfuscateCanonicalSignatureCipher(
        playerUrl: String,
        cipher: String,
    ): String? =
        withContext(Dispatchers.Default) {
            operationMutex.withLock operation@{
                try {
                    val params = parseCipherParams(cipher)
                    val url = params["url"] ?: return@operation null
                    val signature = params["s"] ?: return@operation null
                    solveWithZemerConfig(
                        playerUrl = playerUrl,
                        sigValues = listOf(signature),
                        nValues = emptyList(),
                    ).sigByChallenge[signature]?.let { solvedSig ->
                        val sigParam = params["sp"] ?: "signature"
                        return@operation appendQueryParameter(url, sigParam, solvedSig)
                    }
                    solveWithGitHubConfig(
                        playerUrl = playerUrl,
                        sigValues = listOf(signature),
                        nValues = emptyList(),
                    ).sigByChallenge[signature]
                        ?.let { solvedSig ->
                            val sigParam = params["sp"] ?: "signature"
                            return@operation appendQueryParameter(url, sigParam, solvedSig)
                        }
                    val solver = getOrCreateSolver(playerUrl)
                    val solvedSig =
                        solver.sigSolver(signature).takeUnless { it.isNullOrBlank() }
                            ?: run {
                                val r =
                                    ejs.solve(
                                        playerUrl,
                                        { getOrDownloadPlayerCode(playerUrl).code },
                                        listOf("sig" to listOf(signature)),
                                        preferPreprocessed = true,
                                    )
                                r.sigByChallenge[signature]
                            }
                    if (solvedSig != null) {
                        val sigParam = params["sp"] ?: "signature"
                        appendQueryParameter(url, sigParam, solvedSig)
                    } else {
                        null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }

    /**
     * Process the n-parameter in a URL to prevent throttling.
     *
     * @param playerUrl The URL of the YouTube player JS
     * @param url The URL with n parameter
     * @return The URL with processed n parameter or null if failed
     */
    suspend fun processNParameter(
        playerUrl: String,
        url: String,
    ): String? = processCanonicalNParameter(canonicalPlayerUrl(playerUrl), url)

    private suspend fun processCanonicalNParameter(
        playerUrl: String,
        url: String,
    ): String? =
        withContext(Dispatchers.Default) {
            operationMutex.withLock operation@{
                try {
                    val nMatch = url.findNParameter() ?: return@operation null
                    val nValue = nMatch.groupValues[1]
                    solveWithZemerConfig(
                        playerUrl = playerUrl,
                        sigValues = emptyList(),
                        nValues = listOf(nValue),
                    ).nByChallenge[nValue]?.let { solvedN ->
                        return@operation replaceQueryParameter(url, nMatch, solvedN)
                    }
                    solveWithGitHubConfig(
                        playerUrl = playerUrl,
                        sigValues = emptyList(),
                        nValues = listOf(nValue),
                    ).nByChallenge[nValue]
                        ?.let { solvedN ->
                            return@operation replaceQueryParameter(url, nMatch, solvedN)
                        }
                    val solver = getOrCreateSolver(playerUrl)
                    val solvedN =
                        solver.nSolver(nValue).takeUnless { it.isNullOrBlank() }
                            ?: run {
                                val r =
                                    ejs.solve(
                                        playerUrl,
                                        { getOrDownloadPlayerCode(playerUrl).code },
                                        listOf("n" to listOf(nValue)),
                                        preferPreprocessed = true,
                                    )
                                r.nByChallenge[nValue]
                            }
                    if (solvedN != null) {
                        replaceQueryParameter(url, nMatch, solvedN)
                    } else {
                        null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }

    private suspend fun solveWithZemerConfig(
        playerUrl: String,
        sigValues: List<String>,
        nValues: List<String>,
    ): RemoteSolveResult {
        if (sigValues.isEmpty() && nValues.isEmpty()) return RemoteSolveResult(emptyMap(), emptyMap())
        val configStore = remotePlayerConfigStore ?: return RemoteSolveResult(emptyMap(), emptyMap())
        var config = configStore.getConfig(playerUrl)
        if (config == null) {
            configStore.forceRefresh(
                missingHash = RemotePlayerConfigParser.extractPlayerHash(playerUrl),
            )
            config = configStore.getConfig(playerUrl)
        }
        val activeConfig = config ?: return RemoteSolveResult(emptyMap(), emptyMap())
        val faradayPlayerUrl = faradayCipherPlayerUrl(playerUrl) ?: return RemoteSolveResult(emptyMap(), emptyMap())

        return try {
            val playerCode = getOrDownloadPlayerCode(faradayPlayerUrl).code
            val solver = getOrCreateZemerSolver(faradayPlayerUrl, playerCode, activeConfig, configStore.configEpoch)
            RemoteSolveResult(
                sigByChallenge =
                    sigValues
                        .distinct()
                        .mapNotNull { challenge ->
                            solver.solveSignature(challenge)?.let { challenge to it }
                        }.toMap(),
                nByChallenge =
                    nValues
                        .distinct()
                        .mapNotNull { challenge ->
                            solver.solveN(challenge)?.let { challenge to it }
                        }.toMap(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.d(TAG, "Faraday zemer config failed player=${playerUrl.logId()} type=${e.logType()}")
            RemoteSolveResult(emptyMap(), emptyMap())
        }
    }

    private suspend fun solveWithGitHubConfig(
        playerUrl: String,
        sigValues: List<String>,
        nValues: List<String>,
    ): RemoteSolveResult {
        val client = githubPreprocessedPlayer ?: return RemoteSolveResult(emptyMap(), emptyMap())
        val result = client.solve(playerUrl, sigValues, nValues)
        return RemoteSolveResult(result.sigByChallenge, result.nByChallenge)
    }

    private suspend fun getOrCreateZemerSolver(
        playerUrl: String,
        playerCode: String,
        config: RemotePlayerConfigParser.HardcodedPlayerConfig,
        configEpoch: Long,
    ): ZemerCipherSolver {
        var evicted = emptyList<CachedZemerSolver>()
        var replaced: CachedZemerSolver? = null
        val solver =
            cacheMutex.withLock {
                zemerCache[playerUrl]?.let { cached ->
                    if (cached.config == config && cached.configEpoch == configEpoch) {
                        zemerCache.remove(playerUrl)
                        zemerCache[playerUrl] = cached
                        return@withLock cached.solver
                    }
                }
                ZemerCipherSolver.create(playerCode, config).also { created ->
                    replaced = zemerCache.remove(playerUrl)
                    zemerCache[playerUrl] =
                        CachedZemerSolver(
                            config = config,
                            configEpoch = configEpoch,
                            solver = created,
                        )
                    evicted = trimZemerCacheLocked()
                }
            }
        replaced?.solver?.dispose()
        evicted.forEach { it.solver.dispose() }
        return solver
    }

    private suspend fun getOrCreateSolver(playerUrl: String): CachedSolver {
        cacheMutex.withLock { solverCacheHitLocked(playerUrl) }?.let { return it }
        val playerCode = getOrDownloadPlayerCode(playerUrl).code
        return getOrCreateSolver(playerUrl, playerCode)
    }

    private suspend fun getOrCreateSolver(
        playerUrl: String,
        playerCode: String,
    ): CachedSolver {
        var evicted = emptyList<CachedSolver>()
        val solver =
            cacheMutex.withLock {
                solverCacheHitLocked(playerUrl) ?: createSolver(playerUrl, playerCode).also { created ->
                    cache[playerUrl] = created
                    evicted = trimSolverCacheLocked()
                }
            }
        evicted.forEach { it.engine.dispose() }
        return solver
    }

    private suspend fun getOrDownloadPlayerCode(playerUrl: String): PlayerCodeResult {
        var cachedCode: String? = null
        var download: CompletableDeferred<String>? = null
        var ownsDownload = false
        cacheMutex.withLock {
            cachedCode = playerCodeCache.remove(playerUrl)?.also { playerCodeCache[playerUrl] = it }
            if (cachedCode == null) {
                download = playerCodeDownloads[playerUrl]
                if (download == null) {
                    download = CompletableDeferred()
                    playerCodeDownloads[playerUrl] = download
                    ownsDownload = true
                }
            }
        }

        cachedCode?.let { return PlayerCodeResult(it, "preloaded") }

        val activeDownload = download ?: return PlayerCodeResult(downloadPlayerScript(playerUrl), "downloaded")
        if (!ownsDownload) {
            return PlayerCodeResult(activeDownload.await(), "in-flight")
        }

        try {
            val playerCode = downloadPlayerScript(playerUrl)
            cacheMutex.withLock {
                playerCodeCache[playerUrl] = playerCode
                trimPlayerCodeCacheLocked()
                if (playerCodeDownloads[playerUrl] === activeDownload) {
                    playerCodeDownloads.remove(playerUrl)
                }
            }
            activeDownload.complete(playerCode)
            return PlayerCodeResult(playerCode, "downloaded")
        } catch (e: CancellationException) {
            cacheMutex.withLock {
                if (playerCodeDownloads[playerUrl] === activeDownload) {
                    playerCodeDownloads.remove(playerUrl)
                }
            }
            activeDownload.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            cacheMutex.withLock {
                if (playerCodeDownloads[playerUrl] === activeDownload) {
                    playerCodeDownloads.remove(playerUrl)
                }
            }
            activeDownload.completeExceptionally(e)
            throw e
        }
    }

    /**
     * Download the YouTube player JavaScript file.
     */
    private suspend fun downloadPlayerScript(playerUrl: String): String {
        val validatedUrl = validatedPlayerScriptUrl(playerUrl)
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                val response =
                    httpClient.getTextWithoutRedirects(validatedUrl, MAX_PLAYER_SCRIPT_BYTES) {
                        header(HttpHeaders.UserAgent, OKHTTP_USER_AGENT)
                        header(HttpHeaders.Accept, "*/*")
                        header("Referer", "https://www.youtube.com/")
                    }
                response.body?.let { return it }
                val failure =
                    PlayerScriptHttpException(
                        status = response.status.value,
                        retryable = response.status.value in setOf(408, 425, 429, 500, 502, 503, 504),
                    )
                lastFailure = failure
                if (!failure.retryable || attempt == 2) {
                    throw failure
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
                if ((e is PlayerScriptHttpException && !e.retryable) || attempt == 2) throw e
            }
            delay(250L shl attempt)
        }
        throw lastFailure ?: IllegalStateException("Player script request failed")
    }

    /**
     * Create a solver from the player JavaScript code using the parser (QuickJS _solveN / _solveSig).
     */
    private suspend fun createSolver(
        playerUrl: String,
        playerCode: String,
    ): CachedSolver {
        val parseResult = PlayerScriptParser.parse(playerCode)
        val solverScript = PlayerScriptParser.generateSolverScript(parseResult)

        val solverEngine = QuickJsEngine()
        try {
            solverEngine.initialize()
            solverEngine.setupYoutubeGlobals()
            if (solverScript.isNotBlank()) {
                solverEngine.execute(solverScript)
            }
        } catch (e: Throwable) {
            solverEngine.dispose()
            throw e
        }

        val hasNFunction = parseResult.nFunctionCode != null
        val hasSigFunction = parseResult.sigFunctionCode != null
        val nSolver: suspend (String) -> String? = { input ->
            if (hasNFunction) {
                try {
                    solverEngine.callFunction("_solveN", input)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        val sigSolver: suspend (String) -> String? = { input ->
            if (hasSigFunction) {
                try {
                    solverEngine.callFunction("_solveSig", input)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        return CachedSolver(playerUrl, solverEngine, nSolver, sigSolver)
    }

    private fun solverCacheHitLocked(playerUrl: String): CachedSolver? = cache.remove(playerUrl)?.also { cache[playerUrl] = it }

    private fun trimSolverCacheLocked(): List<CachedSolver> {
        val evicted = mutableListOf<CachedSolver>()
        while (cache.size > MAX_PLAYER_CACHE_ENTRIES) {
            val oldestKey = cache.keys.firstOrNull() ?: break
            cache.remove(oldestKey)?.let(evicted::add)
        }
        return evicted
    }

    private fun trimZemerCacheLocked(): List<CachedZemerSolver> {
        val evicted = mutableListOf<CachedZemerSolver>()
        while (zemerCache.size > MAX_PLAYER_CACHE_ENTRIES) {
            val oldestKey = zemerCache.keys.firstOrNull() ?: break
            zemerCache.remove(oldestKey)?.let(evicted::add)
        }
        return evicted
    }

    private fun trimPlayerCodeCacheLocked() {
        // Keep only the current raw player; parser solvers no longer retain another copy.
        while (playerCodeCache.size > 1) {
            val oldestKey = playerCodeCache.keys.firstOrNull() ?: break
            playerCodeCache.remove(oldestKey)
        }
    }

    /**
     * Parse cipher parameters from a cipher string.
     */
    private fun parseCipherParams(cipher: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val pairs = cipher.split("&")

        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                params[parts[0]] = decodeQueryComponent(parts[1])
            }
        }

        return params
    }

    internal fun appendQueryParameter(
        url: String,
        name: String,
        value: String,
    ): String? {
        if (!url.isApprovedMediaUrl() ||
            !QUERY_PARAMETER_NAME_REGEX.matches(name) ||
            value.isBlank() ||
            value.length > MAX_SOLVER_OUTPUT_LENGTH
        ) {
            return null
        }
        val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
        val hasQuery = url.indexOf('?').let { it >= 0 && it < fragmentStart }
        val separator =
            when {
                !hasQuery -> "?"
                fragmentStart > 0 && url[fragmentStart - 1] in "?&" -> ""
                else -> "&"
            }
        return buildString(url.length + name.length + value.length + 2) {
            append(url, 0, fragmentStart)
            append(separator)
            append(name)
            append('=')
            append(encodeQueryComponent(value))
            append(url, fragmentStart, url.length)
        }
    }

    internal fun replaceQueryParameter(
        url: String,
        match: MatchResult,
        value: String,
    ): String? {
        val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
        if (!url.isApprovedMediaUrl() ||
            match.range.first >= fragmentStart ||
            value.isBlank() ||
            value.length > MAX_SOLVER_OUTPUT_LENGTH
        ) {
            return null
        }
        val start = match.range.last + 1 - match.groupValues[1].length
        return url.replaceRange(start, match.range.last + 1, encodeQueryComponent(value))
    }

    private fun encodeQueryComponent(value: String): String =
        buildString(value.length) {
            value.encodeToByteArray().forEach { byte ->
                val c = byte.toInt() and 0xff
                if (c in 0x30..0x39 || c in 0x41..0x5a || c in 0x61..0x7a || c == '-'.code || c == '.'.code || c == '_'.code ||
                    c == '~'.code
                ) {
                    append(c.toChar())
                } else {
                    append('%')
                    append("0123456789ABCDEF"[c ushr 4])
                    append("0123456789ABCDEF"[c and 15])
                }
            }
        }

    private fun String.findNParameter(): MatchResult? {
        if (!isApprovedMediaUrl()) return null
        val fragmentStart = indexOf('#').takeIf { it >= 0 } ?: length
        return N_PARAMETER_REGEX.find(this)?.takeIf { it.range.first < fragmentStart }
    }

    private fun String.extractNParameter(): String? = findNParameter()?.groupValues?.get(1)

    private fun String.isApprovedMediaUrl(): Boolean {
        if (length !in 1..MAX_MEDIA_URL_LENGTH) return false
        val url = runCatching { Url(this) }.getOrNull() ?: return false
        val isMediaHost =
            url.host == "googlevideo.com" || url.host.endsWith(".googlevideo.com") ||
                url.host == "youtube.com" || url.host.endsWith(".youtube.com")
        return url.protocol.name == "https" &&
            url.port == 443 &&
            isMediaHost &&
            url.encodedPath == "/videoplayback" &&
            url.user == null &&
            url.password == null
    }

    private fun Map<String, String>.hasValidSignatureParameter(): Boolean = get("sp")?.let(QUERY_PARAMETER_NAME_REGEX::matches) != false

    /**
     * Clean up resources.
     */
    suspend fun dispose() =
        operationMutex.withLock {
            val (cachedSolvers, cachedZemerSolvers) =
                cacheMutex.withLock {
                    val solvers = cache.values.toList()
                    val zemerSolvers = zemerCache.values.toList()
                    cache.clear()
                    zemerCache.clear()
                    playerCodeCache.clear()
                    playerCodeDownloads.clear()
                    solvers to zemerSolvers
                }
            cachedSolvers.forEach { it.engine.dispose() }
            cachedZemerSolvers.forEach { it.solver.dispose() }
            ejs.dispose()
        }

    private fun String.logId(): String = RemotePlayerConfigParser.extractPlayerHash(this) ?: "unknown"

    private fun Throwable.logType(): String = this::class.simpleName ?: "Exception"
}

internal fun validatedPlayerScriptUrl(value: String): Url {
    val url = Url(value)
    val isYouTubeHost =
        url.host == "youtube.com" ||
            url.host.endsWith(".youtube.com") ||
            url.host == "youtube-nocookie.com" ||
            url.host.endsWith(".youtube-nocookie.com")
    require(
        url.protocol.name == "https" &&
            isYouTubeHost &&
            url.encodedPath.startsWith("/s/player/") &&
            url.encodedPath.endsWith(".js"),
    ) {
        "Player script URL must use an approved YouTube HTTPS endpoint"
    }
    return url
}
