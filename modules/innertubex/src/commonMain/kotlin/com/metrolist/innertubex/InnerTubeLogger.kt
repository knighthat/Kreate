package com.metrolist.innertubex

enum class InnerTubeLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class InnerTubeLogEvent(
    val level: InnerTubeLogLevel,
    val tag: String,
    val message: String,
    val mediaId: String? = null,
    val details: Map<String, String> = emptyMap(),
)

fun interface InnerTubeLogger {
    fun log(event: InnerTubeLogEvent)

    companion object {
        val NONE = InnerTubeLogger {}
    }
}

internal fun InnerTubeLogger.d(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.DEBUG, tag, message, mediaId, details))

internal fun InnerTubeLogger.i(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.INFO, tag, message, mediaId, details))

internal fun InnerTubeLogger.w(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.WARN, tag, message, mediaId, details))

internal fun InnerTubeLogger.e(
    tag: String,
    message: String,
    mediaId: String? = null,
    details: Map<String, String> = emptyMap(),
) = log(InnerTubeLogEvent(InnerTubeLogLevel.ERROR, tag, message, mediaId, details))
