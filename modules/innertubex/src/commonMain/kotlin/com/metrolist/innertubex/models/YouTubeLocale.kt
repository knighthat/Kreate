package com.metrolist.innertubex.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeLocale(
    val gl: String,
    val hl: String,
) {
    internal fun acceptLanguageHeader(): String {
        val languageTag = hl.replace('_', '-')
        val regionalTag = if ('-' in languageTag || gl.isEmpty()) languageTag else "$languageTag-$gl"
        val fallback = languageTag.substringBefore('-')
        return if (regionalTag == fallback) regionalTag else "$regionalTag,$fallback;q=0.9"
    }
}
