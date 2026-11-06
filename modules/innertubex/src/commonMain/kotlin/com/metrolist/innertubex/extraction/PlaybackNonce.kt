package com.metrolist.innertubex.extraction

import io.ktor.http.Url
import kotlin.random.Random

public fun generateClientPlaybackNonce(): String =
    buildString(16) {
        repeat(16) { append(CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)]) }
    }

public fun appendClientPlaybackNonce(
    url: String,
    clientPlaybackNonce: String,
): String {
    if (!validNonce(clientPlaybackNonce)) return url
    val parsed = approvedMediaUrl(url) ?: return url
    if (parsed.parameters.contains("cpn")) return url
    return addQueryBeforeFragment(url, "cpn=$clientPlaybackNonce")
}

public fun replaceClientPlaybackNonce(
    url: String,
    clientPlaybackNonce: String,
): String {
    if (!validNonce(clientPlaybackNonce)) return url
    if (approvedMediaUrl(url) == null) return url
    val match = CPN_REGEX.find(url)
    return if (match == null) {
        appendClientPlaybackNonce(url, clientPlaybackNonce)
    } else {
        url.replaceRange(match.range, "${match.groupValues[1]}cpn=$clientPlaybackNonce")
    }
}

private fun approvedMediaUrl(value: String): Url? =
    runCatching { Url(value) }.getOrNull()?.takeIf {
        it.protocol.name == "https" &&
            it.port == 443 &&
            (it.host == "googlevideo.com" || it.host.endsWith(".googlevideo.com")) &&
            it.encodedPath == "/videoplayback" &&
            it.user == null && it.password == null
    }

private fun addQueryBeforeFragment(
    url: String,
    parameter: String,
): String {
    val fragmentIndex = url.indexOf('#')
    val beforeFragment = if (fragmentIndex < 0) url else url.substring(0, fragmentIndex)
    val fragment = if (fragmentIndex < 0) "" else url.substring(fragmentIndex)
    return beforeFragment + (if ('?' in beforeFragment) "&$parameter" else "?$parameter") + fragment
}

private fun validNonce(value: String): Boolean = value.length == 16 && value.all { it in CPN_ALPHABET }

private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
private val CPN_REGEX = Regex("([?&])cpn=[^&#]*")
