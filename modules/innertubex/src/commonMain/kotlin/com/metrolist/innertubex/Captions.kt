package com.metrolist.innertubex

import com.metrolist.innertubex.cipher.getTextWithoutRedirects
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.GetTranscriptResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

public data class TranscriptCue(
    val text: String,
    val startMs: Long,
    val durationMs: Long,
) {
    override fun toString(): String =
        "TranscriptCue(text=${if (text.isBlank()) "missing" else "present"}, startMs=$startMs, durationMs=$durationMs)"
}

/** Decodes a bounded JSON object without relying on an unbounded content-negotiation read. */
public suspend fun HttpResponse.bodyAsJsonObjectLimited(maxBytes: Int): JsonObject =
    RESPONSE_JSON.parseToJsonElement(bodyAsTextLimited(maxBytes)).jsonObject

/** Requests and parses transcript cues with bounded response, cue-count, and cue-text allocations. */
public suspend fun InnerTube.transcriptCues(
    client: YouTubeClient,
    videoId: String,
): List<TranscriptCue> {
    require(SAFE_VIDEO_ID.matches(videoId)) { "Invalid video ID" }
    val response = getTranscript(client, videoId)
    val transcript = RESPONSE_JSON.decodeFromString<GetTranscriptResponse>(response.bodyAsTextLimited(MAX_TRANSCRIPT_BYTES))
    val groups =
        transcript.actions
            ?.firstOrNull()
            ?.updateEngagementPanelAction
            ?.content
            ?.transcriptRenderer
            ?.body
            ?.transcriptBodyRenderer
            ?.cueGroups
            .orEmpty()
    require(groups.size <= MAX_TRANSCRIPT_CUES) { "Transcript cue count exceeded the supported limit" }
    return groups.mapNotNull { group ->
        val cue =
            group.transcriptCueGroupRenderer
                ?.cues
                ?.firstOrNull()
                ?.transcriptCueRenderer ?: return@mapNotNull null
        val text =
            cue.cue
                ?.simpleText
                ?.trim('\u266a')
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.length <= MAX_TRANSCRIPT_CUE_TEXT_LENGTH }
                ?: return@mapNotNull null
        TranscriptCue(
            text = text,
            startMs = cue.startOffsetMs ?: return@mapNotNull null,
            durationMs = cue.durationMs ?: 0L,
        )
    }
}

/** Fetches a public YouTube caption track without redirects, cookies, or account headers. */
public suspend fun InnerTube.fetchCaptionText(url: String): String {
    val captionUrl = validatedCaptionUrl(url)
    val response = httpClient.getTextWithoutRedirects(captionUrl, MAX_CAPTION_BYTES)
    if (!response.status.isSuccess()) {
        throw InnerTubeHttpException("fetchCaptionText", response.status)
    }
    return checkNotNull(response.body)
}

private fun validatedCaptionUrl(value: String): Url {
    val url = Url(value)
    val validEndpoint =
        when (url.host) {
            "youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com" -> {
                url.encodedPath == "/api/timedtext"
            }

            "video.google.com" -> {
                url.encodedPath == "/timedtext"
            }

            else -> {
                false
            }
        }
    require(url.protocol.name == "https" && url.port == 443 && validEndpoint && url.user == null && url.password == null) {
        "Caption URL must use an approved YouTube HTTPS endpoint"
    }
    return url
}

private val RESPONSE_JSON = Json { ignoreUnknownKeys = true }
private val SAFE_VIDEO_ID = Regex("[A-Za-z0-9_-]{1,64}")
private const val MAX_TRANSCRIPT_BYTES = 4 * 1024 * 1024
private const val MAX_TRANSCRIPT_CUES = 20_000
private const val MAX_TRANSCRIPT_CUE_TEXT_LENGTH = 16 * 1024
private const val MAX_CAPTION_BYTES = 4 * 1024 * 1024
