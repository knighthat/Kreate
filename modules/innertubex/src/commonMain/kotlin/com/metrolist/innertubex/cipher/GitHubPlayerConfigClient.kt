package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.d
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Legacy GitHub-hosted preprocessed-player config fallback.
 *
 * The config provides a validated yt-dlp EJS preprocessed player for known
 * YouTube player hashes. The app still computes each per-stream challenge
 * locally and falls back to the built-in full player solver on any failure.
 */
internal class GitHubPlayerConfigClient(
    private val httpClient: HttpClient,
    private val ejs: EjsChallengeSolver,
    private val repository: PlayerConfigRepository,
    private val validateSourceUrl: (String) -> Url?,
    private val logger: InnerTubeLogger,
) {
    companion object {
        private const val TAG = "GitHubPlayerConfigClient"
        private const val REQUEST_TIMEOUT_MS = 2_000L
        private const val CONFIG_TTL_MS = 24 * 60 * 60 * 1000L
        private const val MISSING_CONFIG_TTL_MS = 15 * 60 * 1000L
        private const val MAX_CONFIG_CACHE_ENTRIES = 8
        private const val MAX_CONFIG_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val PREPROCESSED_TYPE = "yt-dlp-ejs-preprocessed-player"
        private const val OKHTTP_USER_AGENT = "okhttp/5.4.0"
        private const val LEGACY_DEFAULT_CONFIG_URL =
            "https://cdn.jsdelivr.net/gh/MetrolistGroup/faraday@{playerTag}/registry/players/{playerHash}.json"
        private const val LEGACY_RELEASE_CONFIG_URL =
            "https://github.com/MetrolistGroup/faraday/releases/download/{playerTag}/{playerHash}.json"
        private val JSDELIVR_URL_REGEX = Regex("^https://cdn\\.jsdelivr\\.net/gh/([^/]+)/([^@/]+)@([^/]+)/(.+)$")
        private val PLAYER_HASH_REGEX = Regex("/s/player/([^/]+)/")
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val cacheMutex = Mutex()
    private val cachedConfigs = LinkedHashMap<String, CachedConfig>()

    /** URLs that recently had no usable config; retried after [MISSING_CONFIG_TTL_MS]. */
    private val missingConfigs = LinkedHashMap<String, Long>()

    @Serializable
    private data class PlayerConfig(
        val schemaVersion: Int = 0,
        val playerHash: String,
        val nTransform: NTransformConfig? = null,
    )

    @Serializable
    private data class NTransformConfig(
        val type: String,
        val preprocessedPlayerEncoding: String? = null,
        val preprocessedPlayer: String,
    )

    private data class CachedConfig(
        val url: String,
        val config: PlayerConfig,
        val fetchedAtMs: Long,
    )

    data class SolveResult(
        val sigByChallenge: Map<String, String>,
        val nByChallenge: Map<String, String>,
    )

    suspend fun solve(
        playerUrl: String,
        sigValues: List<String>,
        nValues: List<String>,
    ): SolveResult {
        if (sigValues.isEmpty() && nValues.isEmpty()) return SolveResult(emptyMap(), emptyMap())
        if (!repository.enabled) {
            logger.d(TAG, "GitHub preprocessed-player skipped; disabled")
            return SolveResult(emptyMap(), emptyMap())
        }

        val playerHash = playerUrl.extractPlayerHash() ?: return SolveResult(emptyMap(), emptyMap())
        val configuredUrl = repository.sourceUrl.trim()
        val configTemplate =
            if (configuredUrl.isZemerTableUrl() || configuredUrl == LEGACY_RELEASE_CONFIG_URL) {
                LEGACY_DEFAULT_CONFIG_URL
            } else {
                configuredUrl
            }
        val configUrl = configTemplate.urlForPlayerHash(playerHash)
        if (configUrl.isNullOrBlank()) {
            logger.d(TAG, "GitHub preprocessed-player skipped; config URL template is blank")
            return SolveResult(emptyMap(), emptyMap())
        }

        val uniqueSigValues = sigValues.distinct()
        val uniqueValues = nValues.distinct()
        return try {
            val preprocessedPlayer = loadPreprocessedPlayer(configUrl, playerHash) ?: return SolveResult(emptyMap(), emptyMap())

            ejs.cachePreprocessedPlayer(playerUrl, preprocessedPlayer)
            val result =
                ejs.solve(
                    playerUrl = playerUrl,
                    fullPlayerJs = "",
                    requestOrder =
                        buildList {
                            if (uniqueSigValues.isNotEmpty()) add("sig" to uniqueSigValues)
                            if (uniqueValues.isNotEmpty()) add("n" to uniqueValues)
                        },
                )
            SolveResult(
                sigByChallenge = result.sigByChallenge.filterValues { it.isNotBlank() },
                nByChallenge = result.nByChallenge.filterValues { it.isNotBlank() },
            ).also { solved ->
                logger.d(
                    TAG,
                    "GitHub preprocessed-player response sigRequested=${uniqueSigValues.size} sigSolved=${solved.sigByChallenge.size} nRequested=${uniqueValues.size} nSolved=${solved.nByChallenge.size} hash=$playerHash",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.d(TAG, "GitHub preprocessed-player failed (${e.logType()})")
            SolveResult(emptyMap(), emptyMap())
        }
    }

    private suspend fun loadPreprocessedPlayer(
        configUrl: String,
        playerHash: String,
    ): String? {
        for ((index, url) in configUrls(configUrl).withIndex()) {
            val config = loadConfig(url, playerHash)
            val preprocessedPlayer = config?.nTransformFor(playerHash)?.decodedPreprocessedPlayerOrNull()
            if (preprocessedPlayer != null) {
                if (url != configUrl) {
                    logger.d(TAG, "GitHub preprocessed-player used fallback config hash=$playerHash")
                }
                return preprocessedPlayer
            }
            logger.d(TAG, "GitHub preprocessed-player missing config hash=$playerHash source=$index")
        }
        return null
    }

    private suspend fun loadConfig(
        configUrl: String,
        playerHash: String,
    ): PlayerConfig? {
        val now = Clock.System.now().toEpochMilliseconds()
        cacheMutex.withLock {
            cachedConfigs
                .remove(configUrl)
                ?.takeIf { now - it.fetchedAtMs < CONFIG_TTL_MS }
                ?.takeIf { it.config.hasConfigFor(playerHash) }
                ?.let {
                    cachedConfigs[configUrl] = it
                    return it.config
                }
            missingConfigs[configUrl]?.let { missingAt ->
                if (now - missingAt in 0 until MISSING_CONFIG_TTL_MS) return null
                missingConfigs.remove(configUrl)
            }
        }

        val fetched = fetchConfig(configUrl)?.takeIf { it.hasConfigFor(playerHash) }
        if (fetched == null) {
            cacheMutex.withLock {
                missingConfigs[configUrl] = Clock.System.now().toEpochMilliseconds()
                while (missingConfigs.size > MAX_CONFIG_CACHE_ENTRIES) {
                    missingConfigs.remove(missingConfigs.keys.firstOrNull() ?: break)
                }
            }
            return null
        }
        cacheMutex.withLock {
            cachedConfigs[configUrl] = CachedConfig(configUrl, fetched, Clock.System.now().toEpochMilliseconds())
            trimConfigCacheLocked()
        }
        return fetched
    }

    private fun trimConfigCacheLocked() {
        // A config may embed several MB of player code; retain only the current player.
        while (cachedConfigs.size > 1) {
            val oldestKey = cachedConfigs.keys.firstOrNull() ?: break
            cachedConfigs.remove(oldestKey)
        }
    }

    private fun configUrls(configUrl: String): List<String> =
        buildList {
            add(configUrl)
            configUrl.jsdelivrRawGitHubFallback()?.let { fallback ->
                if (fallback !in this) add(fallback)
            }
        }

    private fun String.jsdelivrRawGitHubFallback(): String? {
        val match = JSDELIVR_URL_REGEX.find(this) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val ref = match.groupValues[3]
        val path = match.groupValues[4]
        return "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
    }

    private suspend fun fetchConfig(configUrl: String): PlayerConfig? =
        try {
            val validatedUrl = validateSourceUrl(configUrl) ?: return null
            val response =
                httpClient.getTextWithoutRedirects(validatedUrl, MAX_CONFIG_RESPONSE_BYTES) {
                    header(HttpHeaders.UserAgent, OKHTTP_USER_AGENT)
                    header(HttpHeaders.Accept, "application/json")
                    timeout {
                        requestTimeoutMillis = REQUEST_TIMEOUT_MS
                        connectTimeoutMillis = REQUEST_TIMEOUT_MS
                        socketTimeoutMillis = REQUEST_TIMEOUT_MS
                    }
                }
            val body = response.body ?: return null
            json.decodeFromString<PlayerConfig>(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.d(TAG, "GitHub config fetch failed (${e.logType()})")
            null
        }

    private fun PlayerConfig.hasConfigFor(playerHash: String): Boolean = nTransformFor(playerHash) != null

    private fun PlayerConfig.nTransformFor(playerHash: String): NTransformConfig? =
        takeIf { it.playerHash == playerHash }
            ?.nTransform
            ?.takeIf { it.type == PREPROCESSED_TYPE && it.preprocessedPlayer.isNotBlank() }

    @OptIn(ExperimentalEncodingApi::class)
    private fun NTransformConfig.decodedPreprocessedPlayerOrNull(): String? =
        when (preprocessedPlayerEncoding) {
            null, "plain" -> {
                preprocessedPlayer.takeIf { it.isNotBlank() }
            }

            "base64" -> {
                try {
                    Base64.decode(preprocessedPlayer).decodeToString().takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    logger.d(TAG, "GitHub preprocessed-player base64 decode failed (${e.logType()})")
                    null
                }
            }

            else -> {
                null
            }
        }

    private fun String.urlForPlayerHash(playerHash: String): String? =
        trim()
            .takeIf { it.isNotBlank() }
            ?.replace("{playerHash}", playerHash)
            ?.replace("{playerTag}", "player-$playerHash")

    private fun String.extractPlayerHash(): String? = PLAYER_HASH_REGEX.find(this)?.groupValues?.getOrNull(1)

    private fun String.isZemerTableUrl(): Boolean = substringBefore('?').endsWith("/player_configs.json")

    private fun Throwable.logType(): String = this::class.simpleName ?: "Exception"
}
