package com.metrolist.music.utils.cipher

import co.touchlab.kermit.Logger
import java.security.MessageDigest

/**
 * Extracts cipher function names from YouTube's player.js
 *
 * Handles both legacy patterns and modern Q-array obfuscation (2025+).
 * Falls back to hardcoded configs for known player.js hashes when regex fails.
 */
object FunctionNameExtractor {
    private const val TAG = "Metrolist_CipherFnExtract"
    
    private val logger = Logger.withTag(TAG)

    // ==================== DATA CLASSES ====================

    data class SigFunctionInfo(
        val name: String,
        val constantArg: Int?, // The first numeric argument (e.g., 48 in JI(48, sig)) - legacy
        val constantArgs: List<Int>? = null, // All constant args e.g., JI(48, 1918, ...) -> [48, 1918]
        val preprocessFunc: String? = null, // Preprocessing function e.g., f1
        val preprocessArgs: List<Int>? = null, // Preprocess args e.g., f1(1, 6528, sig) -> [1, 6528]
        val isHardcoded: Boolean = false
    )

    data class NFunctionInfo(
        val name: String,
        val arrayIndex: Int?, // e.g. FUNC[0] -> index=0
        val constantArgs: List<Int>? = null, // e.g. GU(6, 6010, n) -> [6, 6010]
        val isHardcoded: Boolean = false
    )

    /**
     * Hardcoded player.js configuration for when regex extraction fails
     * Due to Q-array obfuscation, patterns like `.get("n")` become `Q[T^6001]`
     */
    data class HardcodedPlayerConfig(
        val sigFuncName: String,
        val sigConstantArg: Int?, // Legacy single arg
        val sigConstantArgs: List<Int>? = null, // e.g. JI(48, 1918, ...) -> [48, 1918]
        val sigPreprocessFunc: String? = null, // e.g. f1
        val sigPreprocessArgs: List<Int>? = null, // e.g. f1(1, 6528, sig) -> [1, 6528]
        val nFuncName: String,
        val nArrayIndex: Int?,
        val nConstantArgs: List<Int>?, // e.g. GU(6, 6010, n) -> [6, 6010]
        val signatureTimestamp: Int
    )

    // ==================== KNOWN PLAYER CONFIGS ====================

    /**
     * Known player.js configurations indexed by hash
     *
     * Player hash 74edf1a3 (March 2026):
     * - Signature: JI(48, 1918, f1(1, 6528, sig)) -> reverse, swap(0, 57%), reverse
     * - N-transform: GU(6, 6010, n) with 87-element self-referential array
     */
    private val KNOWN_PLAYER_CONFIGS = mapOf(
        "74edf1a3" to HardcodedPlayerConfig(
            sigFuncName = "JI",
            sigConstantArg = 48, // Legacy
            sigConstantArgs = listOf(48, 1918), // JI(48, 1918, processedSig)
            sigPreprocessFunc = "f1", // sig must be preprocessed through f1()
            sigPreprocessArgs = listOf(1, 6528), // f1(1, 6528, sig)
            nFuncName = "GU",
            nArrayIndex = null, // Direct function, not array access
            nConstantArgs = listOf(6, 6010), // GU(6, 6010, n) - the function requires 3 args!
            signatureTimestamp = 20522
        ),
        "f4c47414" to HardcodedPlayerConfig(
            sigFuncName = "hJ",
            sigConstantArg = 6,
            sigConstantArgs = listOf(6), // hJ(6, decodeURIComponent(h.s))
            sigPreprocessFunc = null, // No preprocessing needed
            sigPreprocessArgs = null,
            nFuncName = "", // Will be extracted via regex
            nArrayIndex = null,
            nConstantArgs = null,
            signatureTimestamp = 20543
        ),
        // May 2026: direct URLs, no client-side cipher or n-transform
        "57f5d44f" to HardcodedPlayerConfig(
            sigFuncName = "",
            sigConstantArg = null,
            sigConstantArgs = null,
            sigPreprocessFunc = null,
            sigPreprocessArgs = null,
            nFuncName = "",
            nArrayIndex = null,
            nConstantArgs = null,
            signatureTimestamp = 20591
        )
    )

    // ==================== DETECTION PATTERNS ====================

    // Detect Q-array obfuscation: var Q="...".split("}")
    private val Q_ARRAY_PATTERN = Regex("""var\s+Q\s*=\s*"[^"]+"\s*\.\s*split\s*\(\s*"\}"\s*\)""")

    // Extract player hash from common patterns
    private val PLAYER_HASH_PATTERNS = listOf(
        Regex("""jsUrl['":\s]+[^"']*?/player/([a-f0-9]{8})/"""),
        Regex("""player_ias\.vflset/[^/]+/([a-f0-9]{8})/"""),
        Regex("""/s/player/([a-f0-9]{8})/""")
    )

    // Modern 2025+ signature deobfuscation function patterns
    private val SIG_FUNCTION_PATTERNS = listOf(
        // Pattern 1 (2025+): &&(VAR=FUNC(NUM,decodeURIComponent(VAR))
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)"""),
        // Pattern 1a (April 2026): &&(z=hJ(6,decodeURIComponent(h.s))
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\.\s*[a-z]\s*\)"""),
        // Classic patterns (pre-2025, kept as fallback)
        Regex("""\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\bm=([a-zA-Z0-9${'$'}]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc\s*&&\s*d\.set\([^,]+\s*,\s*(?:encodeURIComponent\s*\()([a-zA-Z0-9$]+)\("""),
        Regex("""\bc\s*&&\s*[a-z]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
    )

    // N-parameter (throttle) transform function patterns
    private val N_FUNCTION_PATTERNS = listOf(
        // Pattern 1: .get("n"))&&(b=FUNC[IDX](VAR)
        Regex("""\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)"""),
        // Pattern 2: .get("n"))&&(FUNC=VAR[IDX](FUNC) (2025+ variant)
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)"""),
        // Pattern 3: .get("n");if(m){var M=n.match... (April 2026 variant)
        Regex("""\.get\("n"\);if\([a-zA-Z0-9$]+\)\s*\{[^}]*match"""),
        // Pattern 4: String.fromCharCode(110) variant (110 = 'n')
        Regex("""\(\s*([a-zA-Z0-9$]+)\s*=\s*String\.fromCharCode\(110\)"""),
        // Pattern 5: enhanced_except_ function pattern
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\([a-zA-Z0-9]\)\s*\{[^}]*?enhanced_except_"""),
    )

    // ==================== EXTRACTION FUNCTIONS ====================

    /**
     * Detect if player.js uses Q-array obfuscation
     */
    fun hasQArrayObfuscation(playerJs: String): Boolean {
        val hasQArray = Q_ARRAY_PATTERN.containsMatchIn(playerJs)
        logger.d("Q-array obfuscation check: hasQArray=$hasQArray")

        if (hasQArray) {
            // Try to count Q array elements for additional info
            val match = Q_ARRAY_PATTERN.find(playerJs)
            if (match != null) {
                val start = match.range.first
                val qDefEnd = playerJs.indexOf(";", start)
                if (qDefEnd > start) {
                    val qDef = playerJs.substring(start, qDefEnd)
                    val elementCount = qDef.count { it == '}' } + 1
                    logger.d("Q-array detected with ~$elementCount elements")
                }
            }
        }
        return hasQArray
    }

    /**
     * Extract player.js hash from embedded URLs or compute from content
     */
    fun extractPlayerHash(playerJs: String): String? {
        logger.d("Extracting player hash from playerJs (${playerJs.length} chars)")

        // Try to extract from embedded URLs first
        for ((index, pattern) in PLAYER_HASH_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val hash = match.groupValues[1]
                logger.d("Player hash found via pattern $index: $hash")
                return hash
            }
        }

        // Fallback: compute hash from first 10KB of content
        val contentToHash = playerJs.take(10000)
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(contentToHash.toByteArray())
        val computedHash = digest.take(4).joinToString("") { "%02x".format(it) }
        logger.d("Player hash computed from content: $computedHash")
        return computedHash
    }

    /**
     * Get hardcoded config for a known player.js hash
     */
    fun getHardcodedConfig(playerHash: String): HardcodedPlayerConfig? {
        val config = KNOWN_PLAYER_CONFIGS[playerHash]
        if (config != null) {
            logger.d("Found hardcoded config for hash $playerHash:")
            logger.d("  sigFunc=${config.sigFuncName}(${config.sigConstantArg}, ...)")
            logger.d("  nFunc=${config.nFuncName}[${config.nArrayIndex}]")
            logger.d("  signatureTimestamp=${config.signatureTimestamp}")
        } else {
            logger.w("No hardcoded config for hash: $playerHash")
            logger.w("Known hashes: ${KNOWN_PLAYER_CONFIGS.keys.joinToString()}")
        }
        return config
    }

    /**
     * Extract signature function info from player.js
     *
     * Uses regex patterns first, falls back to hardcoded config if Q-array detected
     * @param playerJs The player.js content
     * @param knownHash Optional hash for hardcoded config lookup
     */
    fun extractSigFunctionInfo(playerJs: String, knownHash: String? = null): SigFunctionInfo? {
        logger.d("========== EXTRACTING SIG FUNCTION ==========")
        logger.d("Player.js size: ${playerJs.length} chars")

        // Try regex patterns first
        for ((index, pattern) in SIG_FUNCTION_PATTERNS.withIndex()) {
            logger.v("Trying sig pattern $index: ${pattern.pattern.take(60)}...")
            val match = pattern.find(playerJs)
            if (match != null) {
                val name = match.groupValues[1]
                val constArg = if (match.groupValues.size > 2) match.groupValues[2].toIntOrNull() else null
                logger.d("SIG FUNCTION FOUND via pattern $index:")
                logger.d("  name=$name, constantArg=$constArg")
                logger.d("  match context: ...${playerJs.substring(maxOf(0, match.range.first - 20), minOf(playerJs.length, match.range.last + 20))}...")
                return SigFunctionInfo(name, constArg, isHardcoded = false)
            }
        }

        logger.w("No sig pattern matched, checking for Q-array obfuscation...")

        // Check for Q-array obfuscation and use hardcoded fallback
        if (hasQArrayObfuscation(playerJs)) {
            // Use knownHash if provided, otherwise try to extract
            val hashToUse = knownHash ?: extractPlayerHash(playerJs)
            logger.d("Using hash for hardcoded lookup: $hashToUse (knownHash=$knownHash)")
            if (hashToUse != null) {
                val config = getHardcodedConfig(hashToUse)
                if (config != null) {
                    logger.d("USING HARDCODED SIG FUNCTION: ${config.sigFuncName}(${config.sigConstantArgs}, ...)")
                    logger.d("Sig preprocess: ${config.sigPreprocessFunc}(${config.sigPreprocessArgs}, sig)")
                    return SigFunctionInfo(
                        name = config.sigFuncName,
                        constantArg = config.sigConstantArg,
                        constantArgs = config.sigConstantArgs,
                        preprocessFunc = config.sigPreprocessFunc,
                        preprocessArgs = config.sigPreprocessArgs,
                        isHardcoded = true
                    )
                }
            }
        }

        logger.e("========== SIG FUNCTION EXTRACTION FAILED ==========")
        logger.e("Could not find signature deobfuscation function name")
        return null
    }

    /**
     * Extract N-transform function info from player.js
     *
     * Uses regex patterns first, falls back to hardcoded config if Q-array detected
     * @param playerJs The player.js content
     * @param knownHash Optional hash for hardcoded config lookup
     */
    fun extractNFunctionInfo(playerJs: String, knownHash: String? = null): NFunctionInfo? {
        logger.d("========== EXTRACTING N-FUNCTION ==========")
        logger.d("Player.js size: ${playerJs.length} chars")

        // Try regex patterns first
        for ((index, pattern) in N_FUNCTION_PATTERNS.withIndex()) {
            logger.v("Trying n-func pattern $index: ${pattern.pattern.take(60)}...")
            val match = pattern.find(playerJs)
            if (match != null) {
                when (index) {
                    0 -> {
                        val name = match.groupValues[1]
                        val arrayIdx = match.groupValues[2].toIntOrNull()
                        logger.d("N-FUNCTION FOUND via pattern $index:")
                        logger.d("  name=$name, arrayIndex=$arrayIdx")
                        return NFunctionInfo(name, arrayIdx, isHardcoded = false)
                    }
                    1 -> {
                        val name = match.groupValues[2]
                        val arrayIdx = match.groupValues[3].toIntOrNull()
                        logger.d("N-FUNCTION FOUND via pattern $index:")
                        logger.d("  name=$name, arrayIndex=$arrayIdx")
                        return NFunctionInfo(name, arrayIdx, isHardcoded = false)
                    }
                    else -> {
                        // Skip patterns that match but don't expose a usable function name.
                        // E.g. the `.get("n");if(...){var M=n.match...` April 2026 variant has
                        // no capturing groups and reading groupValues[1] would throw.
                        if (pattern.toPattern().matcher("").groupCount() < 1) {
                            logger.d("N-pattern $index matched but has no capture groups; skipping")
                            continue
                        }
                        val name = match.groupValues[1]
                        logger.d("N-FUNCTION FOUND via pattern $index:")
                        logger.d("  name=$name")
                        return NFunctionInfo(name, null, isHardcoded = false)
                    }
                }
            }
        }

        logger.w("No n-func pattern matched, checking for Q-array obfuscation...")

        // Check for Q-array obfuscation and use hardcoded fallback
        if (hasQArrayObfuscation(playerJs)) {
            // Use knownHash if provided, otherwise try to extract
            val hashToUse = knownHash ?: extractPlayerHash(playerJs)
            logger.d("Using hash for hardcoded lookup: $hashToUse (knownHash=$knownHash)")
            if (hashToUse != null) {
                val config = getHardcodedConfig(hashToUse)
                if (config != null) {
                    logger.d("USING HARDCODED N-FUNCTION: ${config.nFuncName}[${config.nArrayIndex}]")
                    logger.d("N-function constant args: ${config.nConstantArgs}")
                    return NFunctionInfo(config.nFuncName, config.nArrayIndex, config.nConstantArgs, isHardcoded = true)
                }
            }
        }

        logger.e("========== N-FUNCTION EXTRACTION FAILED ==========")
        logger.e("Could not find n-transform function name")
        return null
    }

    /**
     * Extract signatureTimestamp from player.js
     */
    fun extractSignatureTimestamp(playerJs: String): Int? {
        logger.d("Extracting signatureTimestamp...")

        val patterns = listOf(
            Regex("""signatureTimestamp['":\s]+(\d+)"""),
            Regex("""sts['":\s]+(\d+)"""),
            Regex(""""signatureTimestamp"\s*:\s*(\d+)""")
        )

        for ((index, pattern) in patterns.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val sts = match.groupValues[1].toIntOrNull()
                if (sts != null) {
                    logger.d("signatureTimestamp found via pattern $index: $sts")
                    return sts
                }
            }
        }

        // Fallback to hardcoded config
        val playerHash = extractPlayerHash(playerJs)
        if (playerHash != null) {
            val config = getHardcodedConfig(playerHash)
            if (config != null) {
                logger.d("Using hardcoded signatureTimestamp: ${config.signatureTimestamp}")
                return config.signatureTimestamp
            }
        }

        logger.w("Could not extract signatureTimestamp")
        return null
    }

    /**
     * Full analysis of player.js - extracts all cipher info
     * @param playerJs The player.js content
     * @param knownHash Optional hash from PlayerJsFetcher (preferred over computed)
     */
    fun analyzePlayerJs(playerJs: String, knownHash: String? = null): PlayerAnalysis {
        logger.d("=== PLAYER.JS CIPHER ANALYSIS ===")

        // Use knownHash from PlayerJsFetcher if provided, otherwise extract/compute
        val playerHash = if (knownHash != null) {
            logger.d("Using known hash from PlayerJsFetcher: $knownHash")
            knownHash
        } else {
            extractPlayerHash(playerJs)
        }

        val hasQArray = hasQArrayObfuscation(playerJs)
        val sigInfo = extractSigFunctionInfo(playerJs, playerHash)
        val nFuncInfo = extractNFunctionInfo(playerJs, playerHash)
        val signatureTimestamp = extractSignatureTimestamp(playerJs)

        logger.d("=== ANALYSIS SUMMARY ===")
        logger.d("Player Hash:        ${playerHash ?: "unknown"}")
        logger.d("Q-Array Obfuscated: $hasQArray")
        logger.d("Sig Function:       ${sigInfo?.name ?: "NOT FOUND"} (hardcoded=${sigInfo?.isHardcoded})")
        logger.d("Sig Constant Arg:   ${sigInfo?.constantArg}")
        logger.d("N-Function:         ${nFuncInfo?.name ?: "NOT FOUND"} (hardcoded=${nFuncInfo?.isHardcoded})")
        logger.d("N-Array Index:      ${nFuncInfo?.arrayIndex}")
        logger.d("Signature TS:       $signatureTimestamp")

        return PlayerAnalysis(
            playerHash = playerHash,
            hasQArrayObfuscation = hasQArray,
            sigInfo = sigInfo,
            nFuncInfo = nFuncInfo,
            signatureTimestamp = signatureTimestamp
        )
    }

    data class PlayerAnalysis(
        val playerHash: String?,
        val hasQArrayObfuscation: Boolean,
        val sigInfo: SigFunctionInfo?,
        val nFuncInfo: NFunctionInfo?,
        val signatureTimestamp: Int?
    )
}
