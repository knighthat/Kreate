package com.metrolist.music.utils.cipher

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main cipher deobfuscation orchestrator for YouTube stream URLs.
 *
 * Handles both signature deobfuscation (for signatureCipher streams) and
 * n-parameter transformation (for throttle avoidance / 403 fix).
 */
object CipherDeobfuscator {
    private const val TAG = "Metrolist_CipherDeobfusc"
    
    private val logger = Logger.withTag(TAG)

    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        logger.d("CipherDeobfuscator initializing...")
        appContext = context.applicationContext
        logger.d("CipherDeobfuscator initialized")
    }

    private var cipherWebView: CipherWebView? = null
    private var currentPlayerHash: String? = null

    /**
     * Deobfuscate a signatureCipher stream URL.
     *
     * The signatureCipher is a query string containing:
     * - s: The obfuscated signature
     * - sp: The signature parameter name (usually "sig" or "signature")
     * - url: The base stream URL
     *
     * Returns the full URL with deobfuscated signature, or null if failed.
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        logger.d("=== DEOBFUSCATE STREAM URL ===")
        logger.d("videoId: $videoId")
        logger.d("signatureCipher length: ${signatureCipher.length}")
        logger.d("signatureCipher preview: ${signatureCipher.take(100)}...")

        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            logger.e("Cipher deobfuscation failed, retrying with fresh JS: ${e.message}", e)
            logger.d("Invalidating cache and retrying...")
            try {
                PlayerJsFetcher.invalidateCache()
                closeWebView()
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
            } catch (retryE: Exception) {
                logger.e("Cipher deobfuscation retry also failed: ${retryE.message}", retryE)
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        logger.d("deobfuscateInternal: videoId=$videoId, isRetry=$isRetry")

        // Parse the signatureCipher query string
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]

        logger.d("Parsed signatureCipher params:")
        logger.d("  s (obfuscated sig): ${obfuscatedSig?.take(30)}... (length=${obfuscatedSig?.length})")
        logger.d("  sp (sig param name): $sigParam")
        logger.d("  url: ${baseUrl?.take(80)}...")

        if (obfuscatedSig == null || baseUrl == null) {
            logger.e("Could not parse signatureCipher params: s=${obfuscatedSig != null}, url=${baseUrl != null}")
            return null
        }

        val webView = getOrCreateWebView(forceRefresh = isRetry)
        if (webView == null) {
            logger.e("Failed to get/create CipherWebView")
            return null
        }

        logger.d("Calling webView.deobfuscateSignature()...")
        val deobfuscatedSig = webView.deobfuscateSignature(obfuscatedSig)
        logger.d("Deobfuscated signature: ${deobfuscatedSig.take(30)}... (length=${deobfuscatedSig.length})")

        // Build the URL with deobfuscated signature
        val separator = if ("?" in baseUrl) "&" else "?"
        val finalUrl = "$baseUrl${separator}${sigParam}=${Uri.encode(deobfuscatedSig)}"

        logger.d("=== CIPHER DEOBFUSCATION SUCCESS ===")
        logger.d("videoId: $videoId")
        logger.d("Final URL length: ${finalUrl.length}")
        logger.d("Final URL preview: ${finalUrl.take(100)}...")

        return finalUrl
    }

    /**
     * Transform the 'n' parameter in a streaming URL to avoid throttling/403.
     *
     * Uses the runtime-discovered n-function from the player JS WebView.
     * Returns the URL with the transformed 'n' value, or the original URL if transform fails.
     *
     * IMPORTANT: This must be called for WEB_REMIX, WEB, WEB_CREATOR, TVHTML5 clients
     * and for privately owned tracks (uploaded songs).
     */
    suspend fun transformNParamInUrl(url: String): String {
        logger.d("=== N-TRANSFORM URL ===")
        logger.d("Input URL length: ${url.length}")
        logger.d("Input URL preview: ${url.take(100)}...")

        return try {
            transformNInternal(url)
        } catch (e: Exception) {
            logger.e("N-transform failed, returning original URL: ${e.message}", e)
            url
        }
    }

    private suspend fun transformNInternal(url: String): String {
        // Extract the 'n' parameter value from the URL
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            logger.d("No 'n' parameter found in URL, skipping transform")
            return url
        }

        val nValueEncoded = nMatch.groupValues[1]
        val nValue = Uri.decode(nValueEncoded)
        logger.d("N-param found:")
        logger.d("  encoded: $nValueEncoded")
        logger.d("  decoded: $nValue")

        val webView = getOrCreateWebView(forceRefresh = false)
        if (webView == null) {
            logger.e("Failed to get CipherWebView for n-transform")
            return url
        }

        logger.d("CipherWebView state:")
        logger.d("  nFunctionAvailable: ${webView.nFunctionAvailable}")
        logger.d("  discoveredNFuncName: ${webView.discoveredNFuncName}")
        logger.d("  usingHardcodedMode: ${webView.usingHardcodedMode}")

        if (!webView.nFunctionAvailable) {
            logger.e("N-transform function was not discovered at init time")
            return url
        }

        logger.d("Calling webView.transformN()...")
        val transformedN = webView.transformN(nValue)

        logger.d("=== N-TRANSFORM SUCCESS ===")
        logger.d("N-param: $nValue -> $transformedN")

        // Replace n= parameter in URL
        val transformedUrl = url.replaceFirst(
            Regex("([?&])n=[^&]+"),
            "$1n=${Uri.encode(transformedN)}"
        )

        logger.d("Transformed URL length: ${transformedUrl.length}")
        return transformedUrl
    }

    private suspend fun getOrCreateWebView(forceRefresh: Boolean): CipherWebView? {
        logger.d("getOrCreateWebView: forceRefresh=$forceRefresh, existing=${cipherWebView != null}")

        if (!forceRefresh && cipherWebView != null) {
            logger.d("Reusing existing CipherWebView (hash=$currentPlayerHash)")
            return cipherWebView
        }

        // Close existing WebView if any
        if (cipherWebView != null) {
            logger.d("Closing existing CipherWebView...")
            closeWebView()
        }

        // Fetch player JS
        logger.d("Fetching player JS...")
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            logger.e("Failed to get player JS")
            return null
        }
        val (playerJs, hash) = result
        logger.d("Got player JS: hash=$hash, length=${playerJs.length}")

        // Run full analysis for logging - pass the known hash from PlayerJsFetcher
        logger.d("Analyzing player JS for cipher functions (knownHash=$hash)...")
        val analysis = FunctionNameExtractor.analyzePlayerJs(playerJs, knownHash = hash)

        if (analysis.sigInfo == null) {
            logger.e("Could not extract signature function info from player JS")
            return null
        }

        if (analysis.nFuncInfo == null) {
            logger.w("Could not extract n-function info from player JS (will try brute-force)")
        }

        logger.d("Creating CipherWebView...")
        logger.d("  sig: ${analysis.sigInfo.name} (constantArg=${analysis.sigInfo.constantArg}, hardcoded=${analysis.sigInfo.isHardcoded})")
        logger.d("  nFunc: ${analysis.nFuncInfo?.name}[${analysis.nFuncInfo?.arrayIndex}] (hardcoded=${analysis.nFuncInfo?.isHardcoded})")

        // Create WebView
        val webView = CipherWebView.create(
            context = appContext,
            playerJs = playerJs,
            sigInfo = analysis.sigInfo,
            nFuncInfo = analysis.nFuncInfo,
        )

        logger.d("CipherWebView created successfully")
        logger.d("  nFunctionAvailable: ${webView.nFunctionAvailable}")
        logger.d("  sigFunctionAvailable: ${webView.sigFunctionAvailable}")
        logger.d("  discoveredNFuncName: ${webView.discoveredNFuncName}")

        cipherWebView = webView
        currentPlayerHash = hash
        return webView
    }

    private suspend fun closeWebView() {
        logger.d("closeWebView: existing=${cipherWebView != null}")
        withContext(Dispatchers.Main) {
            cipherWebView?.close()
        }
        cipherWebView = null
        currentPlayerHash = null
        logger.d("CipherWebView closed and cleared")
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = Uri.decode(pair.substring(0, idx))
                val value = Uri.decode(pair.substring(idx + 1))
                result[key] = value
            }
        }
        logger.v("parseQueryParams: ${result.keys.joinToString()}")
        return result
    }

    /**
     * Debug method: Get current state information
     */
    fun getDebugInfo(): Map<String, Any?> {
        return mapOf(
            "hasWebView" to (cipherWebView != null),
            "playerHash" to currentPlayerHash,
            "nFunctionAvailable" to cipherWebView?.nFunctionAvailable,
            "sigFunctionAvailable" to cipherWebView?.sigFunctionAvailable,
            "discoveredNFuncName" to cipherWebView?.discoveredNFuncName,
            "usingHardcodedMode" to cipherWebView?.usingHardcodedMode,
        )
    }
}
