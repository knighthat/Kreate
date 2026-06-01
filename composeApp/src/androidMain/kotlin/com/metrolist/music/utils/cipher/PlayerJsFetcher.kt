package com.metrolist.music.utils.cipher

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Fetches and caches YouTube's player.js for cipher operations.
 *
 * The player.js contains the signature deobfuscation and n-transform functions
 * that are required to access stream URLs on web clients.
 */
object PlayerJsFetcher : KoinComponent {
    private const val TAG = "Metrolist_CipherFetcher"
    private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
    private const val PLAYER_JS_URL_TEMPLATE = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_GB/base.js"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours

    private val httpClient by inject<OkHttpClient>()
    private val logger = Logger.withTag(TAG)

    // Regex to extract player hash from iframe_api response
    private val PLAYER_HASH_REGEX = Regex("""\\?/s\\?/player\\?/([a-zA-Z0-9_-]+)\\?/""")

    private fun getCacheDir(): File = File(CipherDeobfuscator.appContext.filesDir, "cipher_cache")

    private fun getCacheFile(hash: String): File = File(getCacheDir(), "player_$hash.js")

    private fun getHashFile(): File = File(getCacheDir(), "current_hash.txt")

    /**
     * Get player.js content and hash.
     *
     * Uses cached version if available and not expired, otherwise fetches fresh.
     * Returns Pair(playerJs, hash) or null if failed.
     */
    suspend fun getPlayerJs(forceRefresh: Boolean = false): Pair<String, String>? = withContext(Dispatchers.IO) {
        logger.d("=== GET PLAYER.JS ===")
        logger.d("forceRefresh: $forceRefresh")

        try {
            val cacheDir = getCacheDir()
            if (!cacheDir.exists()) {
                logger.d("Creating cache directory: ${cacheDir.absolutePath}")
                cacheDir.mkdirs()
            }

            // Check cache first (unless forced refresh)
            if (!forceRefresh) {
                val cached = readFromCache()
                if (cached != null) {
                    logger.d("=== CACHE HIT ===")
                    logger.d("Using cached player JS (hash=${cached.second}, length=${cached.first.length})")
                    return@withContext cached
                }
                logger.d("Cache miss, will fetch fresh")
            }

            // Fetch player hash from iframe_api
            logger.d("Fetching player hash from iframe_api...")
            val hash = fetchPlayerHash()
            if (hash == null) {
                logger.e("Failed to extract player hash from iframe_api")
                return@withContext null
            }
            logger.d("Extracted player hash: $hash")

            // Download player JS
            logger.d("Downloading player JS for hash: $hash...")
            val playerJs = downloadPlayerJs(hash)
            if (playerJs == null) {
                logger.e("Failed to download player JS for hash=$hash")
                return@withContext null
            }

            logger.d("=== PLAYER.JS DOWNLOADED ===")
            logger.d("hash: $hash")
            logger.d("length: ${playerJs.length} chars")
            logger.d("preview: ${playerJs.take(100)}...")

            // Cache the result
            writeToCache(hash, playerJs)

            Pair(playerJs, hash)
        } catch (e: Exception) {
            logger.e("getPlayerJs exception: ${e.message}", e)
            null
        }
    }

    /**
     * Invalidate the player.js cache.
     * Call this when cipher operations fail to force a fresh fetch.
     */
    fun invalidateCache() {
        logger.d("Invalidating cache...")
        try {
            val cacheDir = getCacheDir()
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                logger.d("Deleting ${files?.size ?: 0} cache files")
                files?.forEach {
                    logger.v("Deleting: ${it.name}")
                    it.delete()
                }
            }
            logger.d("Cache invalidated successfully")
        } catch (e: Exception) {
            logger.e("Failed to invalidate cache: ${e.message}", e)
        }
    }

    private fun readFromCache(): Pair<String, String>? {
        logger.d("Checking cache...")
        try {
            val hashFile = getHashFile()
            if (!hashFile.exists()) {
                logger.d("Hash file does not exist")
                return null
            }

            val hashData = hashFile.readText().split("\n")
            if (hashData.size < 2) {
                logger.d("Hash file malformed (expected 2 lines, got ${hashData.size})")
                return null
            }

            val hash = hashData[0]
            val timestamp = hashData[1].toLongOrNull()
            if (timestamp == null) {
                logger.d("Could not parse timestamp from hash file")
                return null
            }

            val ageMs = System.currentTimeMillis() - timestamp
            val ageHours = ageMs / (1000 * 60 * 60)
            logger.d("Cache age: ${ageHours}h (TTL: ${CACHE_TTL_MS / (1000 * 60 * 60)}h)")

            // Check TTL
            if (ageMs > CACHE_TTL_MS) {
                logger.d("Cache expired (hash=$hash, age=${ageHours}h)")
                return null
            }

            val cacheFile = getCacheFile(hash)
            if (!cacheFile.exists()) {
                logger.d("Cache file does not exist for hash: $hash")
                return null
            }

            val playerJs = cacheFile.readText()
            if (playerJs.isEmpty()) {
                logger.d("Cache file is empty")
                return null
            }

            logger.d("Cache valid: hash=$hash, length=${playerJs.length}, age=${ageHours}h")
            return Pair(playerJs, hash)
        } catch (e: Exception) {
            logger.e("Error reading cache: ${e.message}", e)
            return null
        }
    }

    private fun writeToCache(hash: String, playerJs: String) {
        logger.d("Writing to cache: hash=$hash, length=${playerJs.length}")
        try {
            val cacheDir = getCacheDir()

            // Clean old cache files
            val oldFiles = cacheDir.listFiles()?.filter { it.name.startsWith("player_") }
            logger.d("Cleaning ${oldFiles?.size ?: 0} old cache files")
            oldFiles?.forEach { it.delete() }

            getCacheFile(hash).writeText(playerJs)
            getHashFile().writeText("$hash\n${System.currentTimeMillis()}")

            logger.d("Cache written successfully")
        } catch (e: Exception) {
            logger.e("Error writing cache: ${e.message}", e)
        }
    }

    private fun fetchPlayerHash(): String? {
        logger.d("Fetching iframe_api from: $IFRAME_API_URL")

        val request = Request.Builder()
            .url(IFRAME_API_URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        logger.d("iframe_api response: HTTP ${response.code}")

        if (!response.isSuccessful) {
            logger.e("iframe_api HTTP ${response.code}")
            return null
        }

        val body = response.body?.string()
        if (body == null) {
            logger.e("iframe_api response body is null")
            return null
        }

        logger.d("iframe_api body length: ${body.length}")
        logger.v("iframe_api body preview: ${body.take(200)}...")

        val match = PLAYER_HASH_REGEX.find(body)
        if (match == null) {
            logger.e("Could not find player hash in iframe_api response")
            logger.d("Regex pattern: ${PLAYER_HASH_REGEX.pattern}")
            return null
        }

        val hash = match.groupValues[1]
        logger.d("Found player hash: $hash")
        return hash
    }

    private fun downloadPlayerJs(hash: String): String? {
        val url = PLAYER_JS_URL_TEMPLATE.format(hash)
        logger.d("Downloading player.js from: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        logger.d("player.js response: HTTP ${response.code}")

        if (!response.isSuccessful) {
            logger.e("player.js download HTTP ${response.code}")
            return null
        }

        val body = response.body?.string()
        if (body == null) {
            logger.e("player.js response body is null")
            return null
        }

        logger.d("player.js downloaded: ${body.length} chars")
        return body
    }

    /**
     * Debug method: Get cache information
     */
    fun getCacheInfo(): Map<String, Any?> {
        return try {
            val hashFile = getHashFile()
            if (!hashFile.exists()) {
                return mapOf("exists" to false)
            }

            val hashData = hashFile.readText().split("\n")
            val hash = hashData.getOrNull(0)
            val timestamp = hashData.getOrNull(1)?.toLongOrNull()
            val cacheFile = hash?.let { getCacheFile(it) }

            mapOf(
                "exists" to true,
                "hash" to hash,
                "timestamp" to timestamp,
                "ageMs" to (timestamp?.let { System.currentTimeMillis() - it }),
                "fileExists" to (cacheFile?.exists() == true),
                "fileSize" to (cacheFile?.length()),
            )
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }
}
