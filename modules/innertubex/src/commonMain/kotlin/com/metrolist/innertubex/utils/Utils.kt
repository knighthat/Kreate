package com.metrolist.innertubex.utils

import io.ktor.http.decodeURLQueryComponent

/** Normalizes a Cookie-header value without exposing or parsing its sensitive contents. */
public fun sanitizeCookieString(cookie: String): String {
    val normalized = cookie.filterNot { it == '\r' || it == '\n' || it == '\t' }
    require(normalized.none { it.isISOControl() }) { "Cookie must not contain control characters" }
    return normalized
        .trim()
        .removePrefixIgnoreCase("Cookie:")
        .trim()
}

/** Joins line-wrapped visitor data; returns null when the result cannot be sent as a single header token. */
internal fun sanitizeVisitorData(visitorData: String?): String? =
    visitorData
        ?.filterNot(Char::isWhitespace)
        ?.takeIf { value -> value.isNotEmpty() && value.all { it in '!'..'~' } }

/** Parses a normalized Cookie-header value. The returned names and values remain sensitive. */
public fun parseCookieString(cookie: String): Map<String, String> =
    sanitizeCookieString(cookie).split(";").map { it.trim() }.filter { it.isNotEmpty() }.associate {
        val parts = it.split("=", limit = 2)
        val rawKey = parts.getOrNull(0)?.trim() ?: ""
        val key = rawKey.removePrefix("Cookie:").removePrefix("cookie:").trim()
        val value = parts.getOrNull(1)?.trim() ?: ""
        key to value
    }

private fun String.removePrefixIgnoreCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

/** Decodes a percent-encoded query component without JVM java.net dependencies. */
internal fun decodeQueryComponent(encoded: String): String = encoded.decodeURLQueryComponent(plusIsSpace = true)

// Expect declaration for platform-specific SHA-1 implementation

/** SHA-1 compatibility hash. Do not use for passwords, signatures, or security decisions. */
public expect fun sha1(input: String): String
