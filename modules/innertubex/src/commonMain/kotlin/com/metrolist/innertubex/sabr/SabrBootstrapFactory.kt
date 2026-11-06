package com.metrolist.innertubex.sabr

import com.metrolist.innertubex.extraction.selectBestVideoFormat
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.PlayerResponse
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalSabrApi
fun PlayerResponse.toSabrBootstrap(
    client: YouTubeClient,
    audioFormat: PlayerResponse.StreamingData.Format,
    poToken: String? = null,
    videoFormat: PlayerResponse.StreamingData.Format? = null,
): SabrBootstrap {
    require(client.useSabr) { "Selected player client is not configured for SABR" }
    return toSabrBootstrap(
        clientId = client.clientId.toInt(),
        clientVersion = client.clientVersion,
        audioFormat = audioFormat,
        poToken = poToken,
        requestUserAgent = client.userAgent,
        requestOrigin = sabrRequestOrigin(client.clientName),
        videoFormat = videoFormat,
    )
}

fun PlayerResponse.toSabrBootstrap(
    clientId: Int,
    clientVersion: String,
    audioFormat: PlayerResponse.StreamingData.Format,
    poToken: String? = null,
    requestUserAgent: String? = null,
    requestOrigin: String? = null,
    serverAbrStreamingUrlOverride: String? = null,
    videoFormat: PlayerResponse.StreamingData.Format? = null,
): SabrBootstrap {
    require(clientId > 0) { "SABR client ID must be positive" }
    val streaming = requireNotNull(streamingData) { "Player response has no streamingData" }
    val serverAbrStreamingUrl =
        serverAbrStreamingUrlOverride
            ?: requireNotNull(streaming.serverAbrStreamingUrl) { "Player response has no serverAbrStreamingUrl" }
    requireAllowedSabrUrl(serverAbrStreamingUrl)
    val ustreamerConfig =
        requireNotNull(
            playerConfig
                ?.mediaCommonConfig
                ?.mediaUstreamerRequestConfig
                ?.videoPlaybackUstreamerConfig,
        ) { "Player response has no videoPlaybackUstreamerConfig" }
    val discardVideo =
        streaming.adaptiveFormats
            .asSequence()
            .filterNot(PlayerResponse.StreamingData.Format::isAudio)
            .filter { it.height != null }
            .minWithOrNull(compareBy({ it.height }, { it.bitrate }))
            ?: error("Player response has no SABR video format to discard")
    val durationMs =
        audioFormat.approxDurationMs?.toLongOrNull()
            ?: videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000)
            ?: error("Player response has no duration")
    require(durationMs > 0) { "Player response has an invalid duration" }
    val audioFormatId = audioFormat.toSabrFormatId()
    val discardVideoFormatId = discardVideo.toSabrFormatId()
    val selectedVideoFormatId = videoFormat?.toSabrFormatId()
    require(audioFormatId.lastModified > 0) { "SABR audio format has no last-modified identity" }
    require(discardVideoFormatId.lastModified > 0) { "SABR video format has no last-modified identity" }
    require(selectedVideoFormatId == null || selectedVideoFormatId.lastModified > 0) {
        "Selected SABR video format has no last-modified identity"
    }
    val videoId = requireNotNull(videoDetails?.videoId) { "Player response has no video ID" }
    val videoVariants =
        if (videoFormat == null) {
            emptyList()
        } else {
            streaming.adaptiveFormats
                .filter { !it.isAudio && (it.height ?: 0) > 0 && (it.lastModified?.toLongOrNull() ?: 0L) > 0L }
                .groupBy { it.height }
                .values
                .mapNotNull { selectBestVideoFormat(it, requireUrl = false) }
                .map {
                    SabrVideoVariant(
                        format = it.toSabrFormatId(),
                        width = it.width,
                        height = checkNotNull(it.height),
                        bitrate = it.averageBitrate?.takeIf { average -> average > 0 } ?: it.bitrate,
                        mimeType = it.mimeType.substringBefore(';').trim(),
                        contentLengthBytes = it.contentLength,
                    )
                }.sortedBy { it.height }
        }

    return SabrBootstrap(
        videoId = videoId,
        serverAbrStreamingUrl = serverAbrStreamingUrl,
        videoPlaybackUstreamerConfig =
            decodeBase64Url(
                value = ustreamerConfig,
                fieldName = "videoPlaybackUstreamerConfig",
                maxDecodedBytes = MAX_USTREAMER_CONFIG_BYTES,
            ),
        clientName = clientId,
        clientVersion = clientVersion,
        audioFormat = audioFormatId,
        discardVideoFormat = discardVideoFormatId,
        discardVideoHeight = discardVideo.height ?: 144,
        selectedVideoFormat = selectedVideoFormatId,
        selectedVideoWidth = videoFormat?.width,
        selectedVideoHeight = videoFormat?.height,
        selectedVideoContentLengthBytes = videoFormat?.contentLength,
        selectedVideoMimeType = videoFormat?.mimeType?.substringBefore(';')?.trim(),
        selectedVideoBitrate = videoFormat?.bitrate,
        durationMs = durationMs,
        contentLengthBytes = audioFormat.contentLength,
        mimeType = audioFormat.mimeType.substringBefore(';').trim(),
        audioTrackId = audioFormat.audioTrack?.id,
        isDrc = audioFormat.isDrc,
        poToken =
            poToken?.takeIf(String::isNotBlank)?.let { value ->
                decodeBase64Url(value, fieldName = "poToken", maxDecodedBytes = MAX_PO_TOKEN_BYTES)
            },
        requestUserAgent = requestUserAgent,
        requestOrigin = requestOrigin,
        videoVariants = videoVariants,
    )
}

fun sabrRequestOrigin(clientName: String): String? =
    when (clientName) {
        "WEB_REMIX" -> "https://music.youtube.com"
        "MWEB" -> "https://m.youtube.com"
        "WEB" -> "https://www.youtube.com"
        else -> null
    }

internal fun SabrFormatId.matches(other: SabrFormatId): Boolean =
    itag == other.itag &&
        (other.lastModified == 0L || lastModified == other.lastModified) &&
        (other.xtags.isNullOrEmpty() || xtags == other.xtags)

fun requireAllowedSabrUrl(value: String): String {
    val url = runCatching { Url(value) }.getOrElse { throw SabrProtocolException("Invalid SABR streaming URL") }
    val host = url.host.lowercase()
    if (
        url.protocol != URLProtocol.HTTPS ||
        url.port != 443 ||
        (host != "googlevideo.com" && !host.endsWith(".googlevideo.com")) ||
        url.encodedPath != "/videoplayback" ||
        url.user != null ||
        url.password != null
    ) {
        throw SabrProtocolException("SABR streaming URL is not an HTTPS googlevideo endpoint")
    }
    return value
}

private fun PlayerResponse.StreamingData.Format.toSabrFormatId(): SabrFormatId =
    SabrFormatId(
        itag = itag,
        lastModified = lastModified?.toLongOrNull() ?: 0,
        xtags = xtags,
    )

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64Url(
    value: String,
    fieldName: String,
    maxDecodedBytes: Int,
): ByteArray {
    val maxEncodedLength = ((maxDecodedBytes + 2) / 3) * 4
    if (value.length > maxEncodedLength) {
        throw SabrProtocolException("Player response $fieldName exceeded the size limit")
    }
    val standard = value.replace('-', '+').replace('_', '/')
    val padded = standard.padEnd(standard.length + (4 - standard.length % 4) % 4, '=')
    val decoded =
        runCatching { Base64.decode(padded) }
            .getOrElse { throw SabrProtocolException("Player response has an invalid $fieldName") }
    if (decoded.size > maxDecodedBytes) {
        throw SabrProtocolException("Player response $fieldName exceeded the size limit")
    }
    return decoded
}

private const val MAX_USTREAMER_CONFIG_BYTES = 64 * 1024
private const val MAX_PO_TOKEN_BYTES = 16 * 1024
