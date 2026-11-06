package com.metrolist.innertubex.extraction.potoken

import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64

private const val ATTESTATION_LIMIT = 4 * 1024 * 1024
private const val INTERPRETER_LIMIT = 8 * 1024 * 1024
private const val INTEGRITY_LIMIT = 1024 * 1024
private const val DECODED_LIMIT = 64 * 1024
private const val LIST_COUNT_LIMIT = 64 * 1024

/** Sensitive integrity token bytes and its server lifetime. Do not log this value. */
public class IntegrityTokenData(
    public val tokenJavaScript: String,
    public val lifetimeSeconds: Long,
) {
    override fun toString(): String =
        "IntegrityTokenData(tokenJavaScript=${tokenJavaScript.length} chars, lifetimeSeconds=$lifetimeSeconds)"
}

public fun parseWebPageAttestationContext(webPage: String): WebPageAttestationContext =
    extractWebPageAttestationContext(webPage)
        ?: throw PoTokenException("Web page has no paired BotGuard challenge and event ID")

public fun parseAttestationInterpreterUrl(rawAttestation: String): String {
    requireSize(rawAttestation, ATTESTATION_LIMIT, "attestation response")
    return runCatching {
        val root = Json.parseToJsonElement(rawAttestation).jsonObject
        requireFieldCount(root)
        val challenge = root["bgChallenge"]?.jsonObject ?: throw PoTokenException("Attestation response has no BotGuard challenge")
        requireFieldCount(challenge)
        val interpreterUrl =
            challenge["interpreterUrl"]?.jsonObject
                ?: throw PoTokenException("Attestation response has no BotGuard interpreter URL")
        requireFieldCount(interpreterUrl)
        val value =
            interpreterUrl["privateDoNotAccessOrElseTrustedResourceUrlWrappedValue"]?.jsonPrimitive?.content
                ?: throw PoTokenException("Attestation response has no BotGuard interpreter URL")
        requireSize(value, INTERPRETER_LIMIT, "interpreter URL")
        requireTrustedAttestationInterpreterUrl(value)
        value
    }.getOrElse { error ->
        if (error is PoTokenException) throw error
        throw PoTokenException("Invalid BotGuard attestation response")
    }
}

public fun requireTrustedAttestationInterpreterUrl(value: String): String {
    requireSize(value, 2048, "interpreter URL")
    val rawUrl = if (value.startsWith("//")) "https:$value" else value
    val url =
        runCatching { Url(rawUrl) }.getOrElse {
            throw PoTokenException("BotGuard returned an invalid interpreter URL")
        }
    if (
        url.protocol.name != "https" ||
        url.port != 443 ||
        url.host !in TRUSTED_INTERPRETER_HOSTS ||
        url.user != null ||
        url.password != null ||
        !isTrustedInterpreterPath(url.host, url.encodedPath)
    ) {
        throw PoTokenException("BotGuard returned an untrusted interpreter URL")
    }
    return url.toString()
}

public fun parseAttestationChallengeData(
    rawAttestation: String,
    interpreterJavascript: String,
): String {
    requireSize(rawAttestation, ATTESTATION_LIMIT, "attestation response")
    requireSize(interpreterJavascript, INTERPRETER_LIMIT, "interpreter")
    return runCatching {
        val root = Json.parseToJsonElement(rawAttestation).jsonObject
        requireFieldCount(root)
        val challenge =
            root["bgChallenge"]?.jsonObject
                ?: throw PoTokenException("Attestation response has no BotGuard challenge")
        requireFieldCount(challenge)
        val interpreterUrl = parseAttestationInterpreterUrl(rawAttestation)
        val result =
            JsonObject(
                mapOf(
                    "interpreterJavascript" to
                        JsonObject(
                            mapOf(
                                "privateDoNotAccessOrElseSafeScriptWrappedValue" to JsonPrimitive(interpreterJavascript),
                                "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to JsonPrimitive(interpreterUrl),
                            ),
                        ),
                    "interpreterHash" to (challenge["interpreterHash"] ?: throw PoTokenException("Challenge is missing interpreter hash")),
                    "program" to (challenge["program"] ?: throw PoTokenException("Challenge is missing program")),
                    "globalName" to (challenge["globalName"] ?: throw PoTokenException("Challenge is missing global name")),
                ),
            )
        Json.encodeToString(JsonObject.serializer(), result)
    }.getOrElse { error ->
        if (error is PoTokenException) throw error
        throw PoTokenException("Invalid BotGuard attestation response")
    }
}

public fun parseIntegrityTokenData(rawIntegrityTokenData: String): IntegrityTokenData {
    requireSize(rawIntegrityTokenData, INTEGRITY_LIMIT, "integrity response")
    return runCatching {
        val data = Json.parseToJsonElement(rawIntegrityTokenData).jsonArray
        if (data.size !in 2..3) throw PoTokenException("Invalid BotGuard integrity response")
        val token = base64ToU8(data[0].jsonPrimitive.content)
        IntegrityTokenData(token, data[1].jsonPrimitive.long)
    }.getOrElse { error ->
        if (error is PoTokenException) throw error
        throw PoTokenException("Invalid BotGuard integrity response")
    }
}

public fun stringToU8(identifier: String): String {
    requireSize(identifier, 1024, "identifier")
    val bytes = identifier.encodeToByteArray()
    if (bytes.size > 1024) throw PoTokenException("identifier exceeds its size limit")
    return newUint8Array(bytes)
}

public fun u8ToBase64(poToken: String): String {
    requireSize(poToken, DECODED_LIMIT, "token byte list")
    val values = poToken.split(',')
    if (values.size > LIST_COUNT_LIMIT || values.any { it.isEmpty() || it.length > 3 }) {
        throw PoTokenException("Invalid BotGuard token byte list")
    }
    val bytes = ByteArray(values.size)
    values.forEachIndexed { index, value ->
        val number = value.toIntOrNull() ?: throw PoTokenException("Invalid BotGuard token byte")
        if (number !in 0..255) throw PoTokenException("BotGuard token byte is out of range")
        bytes[index] = number.toByte()
    }
    return Base64.UrlSafe.encode(bytes)
}

private fun base64ToU8(base64: String): String {
    requireSize(base64, DECODED_LIMIT, "integrity token")
    val normalized = base64.replace('+', '-').replace('/', '_').replace('.', '=')
    if (normalized.length % 4 == 1 || normalized.dropLastWhile { it == '=' }.contains('=')) {
        throw PoTokenException("Cannot decode BotGuard base64 payload")
    }
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val bytes =
        runCatching { Base64.UrlSafe.decode(padded) }.getOrElse {
            throw PoTokenException("Cannot decode BotGuard base64 payload")
        }
    if (bytes.size > DECODED_LIMIT) throw PoTokenException("BotGuard integrity token is too large")
    return newUint8Array(bytes)
}

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([${contents.joinToString(",") { (it.toInt() and 0xff).toString() }}])"

private fun isTrustedInterpreterPath(
    host: String,
    path: String,
): Boolean =
    path == "/botguard.js" ||
        (host == "www.google.com" && path.matches(Regex("^/js/th/[A-Za-z0-9_-]{20,256}\\.js$")))

private val TRUSTED_INTERPRETER_HOSTS = setOf("www.google.com", "www.gstatic.com")
