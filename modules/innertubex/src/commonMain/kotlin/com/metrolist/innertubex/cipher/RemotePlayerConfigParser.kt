package com.metrolist.innertubex.cipher

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

internal object RemotePlayerConfigParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val SIG_RE = Regex($$"""^[A-Za-z0-9$_]{1,8}\(\d+,\d+,INPUT\)$""")
    private val NCLASS_RE = Regex($$"""^[A-Za-z0-9$_]{1,8}$""")
    private val HASH_RE = Regex("""^[a-f0-9]{8}$""")
    private val PLAYER_HASH_PATTERNS =
        listOf(
            Regex("""/player/([a-f0-9]{8})/"""),
            Regex("""player_ias\.vflset/[^/]+/([a-f0-9]{8})/"""),
            Regex("""/s/player/([a-f0-9]{8})/"""),
        )

    data class HardcodedPlayerConfig(
        val sigFuncName: String,
        val sigConstantArg: Int?,
        val sigConstantArgs: List<Int>? = null,
        val sigPreprocessFunc: String? = null,
        val sigPreprocessArgs: List<Int>? = null,
        val sigJsExpression: String? = null,
        val nFuncName: String,
        val nArrayIndex: Int?,
        val nConstantArgs: List<Int>?,
        val nJsExpression: String? = null,
        val signatureTimestamp: Int,
    )

    sealed class ParseResult {
        data class Success(
            val configs: Map<String, HardcodedPlayerConfig>,
            val skippedEntries: List<String>,
        ) : ParseResult()

        data class Failure(
            val reason: String,
        ) : ParseResult()
    }

    fun buildNJsExpression(nClass: String): String =
        "(function(n){try{var u=new g.$nClass('https://x.googlevideo.com/videoplayback?n='+n,true);" +
            "var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)"

    fun parse(jsonText: String): ParseResult {
        val root =
            try {
                Json.parseToJsonElement(jsonText) as? JsonObject
                    ?: return ParseResult.Failure("root is not a JSON object")
            } catch (_: Exception) {
                return ParseResult.Failure("malformed JSON")
            }

        val schemaVersion =
            (root["schemaVersion"] as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.content
                ?.toIntOrNull()
                ?: return ParseResult.Failure("schemaVersion missing or not an int")
        if (schemaVersion <= 0) return ParseResult.Failure("schemaVersion must be positive")
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            return ParseResult.Failure("unsupported schemaVersion $schemaVersion (supported: $SUPPORTED_SCHEMA_VERSION)")
        }

        val players =
            root["players"] as? JsonObject
                ?: return ParseResult.Failure("players missing or not an object")

        val configs = mutableMapOf<String, HardcodedPlayerConfig>()
        val skipped = mutableListOf<String>()

        for ((hash, entryElement) in players) {
            val entry = parseEntry(hash, entryElement as? JsonObject)
            if (entry == null) {
                skipped += hash
                continue
            }
            val (config, aliases) = entry
            val keys = listOf(hash) + aliases
            val duplicate =
                keys.firstOrNull { it in configs }
                    ?: keys
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .firstOrNull { it.value > 1 }
                        ?.key
            if (duplicate != null) {
                return ParseResult.Failure("duplicate hash/alias '$duplicate' (entry $hash)")
            }
            configs[hash] = config
            for (alias in aliases) configs[alias] = config
        }

        return ParseResult.Success(configs, skipped)
    }

    fun extractPlayerHash(playerUrl: String): String? {
        for (pattern in PLAYER_HASH_PATTERNS) {
            val match = pattern.find(playerUrl)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    internal fun parseLegacySignatureTimestamp(
        jsonText: String,
        expectedPlayerHash: String,
    ): Int? {
        val root = runCatching { Json.parseToJsonElement(jsonText) as? JsonObject }.getOrNull() ?: return null
        val playerHash = (root["playerHash"] as? JsonPrimitive)?.content
        if (playerHash != null && playerHash != expectedPlayerHash) return null
        val player = root["player"] as? JsonObject
        return root.positiveInt("signatureTimestamp")
            ?: root.positiveInt("sts")
            ?: player?.positiveInt("signatureTimestamp")
            ?: player?.positiveInt("sts")
    }

    private fun JsonObject.positiveInt(name: String): Int? = (get(name) as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it > 0 }

    private fun parseEntry(
        hash: String,
        obj: JsonObject?,
    ): Pair<HardcodedPlayerConfig, List<String>>? {
        if (obj == null || !HASH_RE.matches(hash)) return null

        val sig = (obj["sig"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (!SIG_RE.matches(sig)) return null

        val nClass = (obj["nClass"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (!NCLASS_RE.matches(nClass)) return null

        val stsPrimitive = (obj["sts"] as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
        val sts = stsPrimitive.content.toIntOrNull() ?: return null
        if (sts <= 0) return null

        val aliases =
            when (val aliasesElement = obj["aliases"]) {
                null -> {
                    emptyList()
                }

                else -> {
                    val array =
                        try {
                            aliasesElement.jsonArray
                        } catch (e: Exception) {
                            return null
                        }
                    array.map { element ->
                        val alias = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                        if (!HASH_RE.matches(alias)) return null
                        alias
                    }
                }
            }

        val config =
            HardcodedPlayerConfig(
                sigFuncName = "_expr_sig",
                sigConstantArg = null,
                sigJsExpression = sig,
                nFuncName = "_expr_n",
                nArrayIndex = null,
                nConstantArgs = null,
                nJsExpression = buildNJsExpression(nClass),
                signatureTimestamp = sts,
            )
        return config to aliases
    }
}

internal fun faradayCipherPlayerUrl(playerUrl: String): String? =
    RemotePlayerConfigParser.extractPlayerHash(playerUrl)?.let { hash ->
        "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_GB/base.js"
    }

private val LOCALIZED_IAS_PLAYER_URL =
    Regex("^https://www\\.youtube\\.com/s/player/([A-Za-z0-9_-]+)/player_ias\\.vflset/[A-Za-z]{2,3}(?:_[A-Za-z]{2,4})?/base\\.js$")

/**
 * Locale variants of the same `player_ias` build are functionally identical. Mapping them to the variant
 * the remote configs are validated against lets every cipher path share one download and cache entry.
 */
internal fun canonicalPlayerUrl(playerUrl: String): String =
    LOCALIZED_IAS_PLAYER_URL.matchEntire(playerUrl)?.let { match ->
        "https://www.youtube.com/s/player/${match.groupValues[1]}/player_ias.vflset/en_GB/base.js"
    } ?: playerUrl
