package com.metrolist.innertubex.sabr

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "SABR support is experimental and the wire protocol can change without notice.",
)
annotation class ExperimentalSabrApi

data class SabrFormatId(
    val itag: Int,
    val lastModified: Long = 0,
    val xtags: String? = null,
)

data class SabrBootstrap(
    val videoId: String,
    val serverAbrStreamingUrl: String,
    val videoPlaybackUstreamerConfig: ByteArray,
    val clientName: Int,
    val clientVersion: String,
    val audioFormat: SabrFormatId,
    val discardVideoFormat: SabrFormatId,
    val discardVideoHeight: Int,
    val selectedVideoFormat: SabrFormatId? = null,
    val selectedVideoWidth: Int? = null,
    val selectedVideoHeight: Int? = null,
    val selectedVideoContentLengthBytes: Long? = null,
    val selectedVideoMimeType: String? = null,
    val selectedVideoBitrate: Int? = null,
    val durationMs: Long,
    val contentLengthBytes: Long?,
    val mimeType: String,
    val audioTrackId: String? = null,
    val isDrc: Boolean = false,
    val poToken: ByteArray? = null,
    val requestUserAgent: String? = null,
    val requestOrigin: String? = null,
    /** Video renditions this session can also stream, one per height, lowest first. */
    val videoVariants: List<SabrVideoVariant> = emptyList(),
) {
    /** Returns this session retargeted at [variant]; audio and session state are unchanged. */
    fun withVideoVariant(variant: SabrVideoVariant): SabrBootstrap {
        require(variant in videoVariants) { "Video variant is not part of this SABR session" }
        return copy(
            selectedVideoFormat = variant.format,
            selectedVideoWidth = variant.width,
            selectedVideoHeight = variant.height,
            selectedVideoContentLengthBytes = variant.contentLengthBytes,
            selectedVideoMimeType = variant.mimeType,
            selectedVideoBitrate = variant.bitrate,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is SabrBootstrap &&
            videoId == other.videoId &&
            serverAbrStreamingUrl == other.serverAbrStreamingUrl &&
            videoPlaybackUstreamerConfig.contentEquals(other.videoPlaybackUstreamerConfig) &&
            clientName == other.clientName &&
            clientVersion == other.clientVersion &&
            audioFormat == other.audioFormat &&
            discardVideoFormat == other.discardVideoFormat &&
            discardVideoHeight == other.discardVideoHeight &&
            selectedVideoFormat == other.selectedVideoFormat &&
            selectedVideoWidth == other.selectedVideoWidth &&
            selectedVideoHeight == other.selectedVideoHeight &&
            selectedVideoContentLengthBytes == other.selectedVideoContentLengthBytes &&
            selectedVideoMimeType == other.selectedVideoMimeType &&
            selectedVideoBitrate == other.selectedVideoBitrate &&
            durationMs == other.durationMs &&
            contentLengthBytes == other.contentLengthBytes &&
            mimeType == other.mimeType &&
            audioTrackId == other.audioTrackId &&
            isDrc == other.isDrc &&
            requestUserAgent == other.requestUserAgent &&
            requestOrigin == other.requestOrigin &&
            videoVariants == other.videoVariants &&
            if (poToken == null) other.poToken == null else other.poToken != null && poToken.contentEquals(other.poToken)

    override fun hashCode(): Int =
        listOf(
            serverAbrStreamingUrl,
            videoPlaybackUstreamerConfig.contentHashCode(),
            clientName,
            clientVersion,
            audioFormat,
            discardVideoFormat,
            discardVideoHeight,
            selectedVideoFormat,
            selectedVideoWidth,
            selectedVideoHeight,
            selectedVideoContentLengthBytes,
            selectedVideoMimeType,
            selectedVideoBitrate,
            durationMs,
            contentLengthBytes,
            mimeType,
            audioTrackId,
            isDrc,
            poToken?.contentHashCode(),
            requestUserAgent,
            requestOrigin,
            videoVariants,
        ).hashCode()
}

data class SabrVideoVariant(
    val format: SabrFormatId,
    val width: Int?,
    val height: Int,
    /** Average bitrate when YouTube reports one, otherwise the peak bitrate. */
    val bitrate: Int,
    val mimeType: String,
    val contentLengthBytes: Long?,
)

internal enum class SabrMediaType {
    AUDIO,
    VIDEO,
}

data class SabrMediaHeader(
    val headerId: Int,
    val videoId: String? = null,
    val formatId: SabrFormatId = SabrFormatId(0),
    val startRange: Long = 0,
    val compressionAlgorithm: Int = 0,
    val isInitSegment: Boolean = false,
    val sequenceNumber: Int = 0,
    val startMs: Long = 0,
    val durationMs: Long = 0,
    val contentLength: Long = 0,
)

data class SabrFormatInitialization(
    val formatId: SabrFormatId,
    val endTimeMs: Long?,
    val endSegmentNumber: Int?,
    val durationMs: Long?,
)

data class SabrSegment(
    val header: SabrMediaHeader,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is SabrSegment && header == other.header && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * header.hashCode() + data.contentHashCode()
}

data class SabrChunk(
    val data: ByteArray,
    val startRange: Long,
    val isInitialization: Boolean,
    val sequenceNumber: Int?,
    val startMs: Long?,
    val durationMs: Long?,
) {
    val endRangeExclusive: Long
        get() {
            require(startRange >= 0L && startRange <= Long.MAX_VALUE - data.size) { "SABR chunk has an invalid byte range" }
            return startRange + data.size
        }

    override fun equals(other: Any?): Boolean =
        other is SabrChunk &&
            startRange == other.startRange &&
            isInitialization == other.isInitialization &&
            sequenceNumber == other.sequenceNumber &&
            startMs == other.startMs &&
            durationMs == other.durationMs &&
            data.contentEquals(other.data)

    override fun hashCode(): Int =
        listOf(
            data.contentHashCode(),
            startRange,
            isInitialization,
            sequenceNumber,
            startMs,
            durationMs,
        ).hashCode()
}

data class SabrBufferedRange(
    val formatId: SabrFormatId,
    val startTimeMs: Long,
    val durationMs: Long,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
)

data class SabrResponseDiagnostics(
    val requestNumber: Int,
    val requestPlayerTimeMs: Long,
    val observedPlaybackPositionMs: Long?,
    val bufferedRanges: List<SabrBufferedRange>,
    val receivedSequences: List<Int>,
    val receivedStartRanges: List<Long>,
    val receivedContentLengths: List<Long>,
    val initializationReceived: Boolean,
    val playbackCookieSize: Int,
    val contextTypes: Set<Int>,
    val activeContextTypes: Set<Int>,
    val targetAudioReadaheadMs: Long,
    val minAudioReadaheadMs: Long,
    val maxTimeSinceLastRequestMs: Long,
    val backoffTimeMs: Long,
    val responseBytes: Long = 0,
    /** Time spent waiting for response body bytes, for throughput estimates. */
    val networkReadMs: Long = 0,
    val selectedMediaBytes: Long = 0,
    val cumulativeMediaBytes: Long = 0,
    val selectedSegmentCount: Int = 0,
    val cumulativeSegmentCount: Int = 0,
    val transientRetryCount: Int = 0,
    val noProgressRequestCount: Int = 0,
    val protectionStatus: Int? = null,
    val protectionMaxRetries: Int? = null,
    val httpStatus: Int? = null,
    val contentType: String? = null,
    val failureCategory: String? = null,
    val failureMessage: String? = null,
)

data class SabrContext(
    val type: Int,
    val value: ByteArray,
    val sendByDefault: Boolean,
    val writePolicy: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is SabrContext &&
            type == other.type &&
            value.contentEquals(other.value) &&
            sendByDefault == other.sendByDefault &&
            writePolicy == other.writePolicy

    override fun hashCode(): Int = (((type * 31) + value.contentHashCode()) * 31 + sendByDefault.hashCode()) * 31 + writePolicy
}

sealed interface SabrEvent {
    data class MediaHeader(
        val header: SabrMediaHeader,
    ) : SabrEvent

    data class Segment(
        val segment: SabrSegment,
    ) : SabrEvent

    data class FormatInitialized(
        val initialization: SabrFormatInitialization?,
    ) : SabrEvent

    data class NextRequestPolicy(
        val targetAudioReadaheadMs: Long,
        val maxTimeSinceLastRequestMs: Long,
        val backoffTimeMs: Long,
        val minAudioReadaheadMs: Long,
        val playbackCookie: ByteArray?,
    ) : SabrEvent

    data class Redirect(
        val url: String,
    ) : SabrEvent

    data class ContextUpdate(
        val context: SabrContext,
    ) : SabrEvent

    data class ContextSendingPolicy(
        val start: Set<Int>,
        val stop: Set<Int>,
        val discard: Set<Int>,
    ) : SabrEvent

    data class StreamProtectionStatus(
        val status: Int,
        /** -1 when the protobuf field is absent (distinct from an explicit zero allowance). */
        val maxRetries: Int,
    ) : SabrEvent

    data class Error(
        val type: String?,
        val code: Int,
    ) : SabrEvent

    data object ReloadPlayerResponse : SabrEvent

    data object EndOfTrack : SabrEvent
}

enum class SabrFailureKind {
    PROTOCOL,
    ATTESTATION_REQUIRED,
    RELOAD_PLAYER,
}

enum class SabrRecoveryAction {
    REFRESH_ATTESTATION,
    EXCLUDE_PROFILE,
}

fun sabrRecoveryAction(
    failureKind: SabrFailureKind?,
    attestationRefreshAttempted: Boolean,
): SabrRecoveryAction =
    if (failureKind == SabrFailureKind.ATTESTATION_REQUIRED && !attestationRefreshAttempted) {
        SabrRecoveryAction.REFRESH_ATTESTATION
    } else {
        SabrRecoveryAction.EXCLUDE_PROFILE
    }

class SabrProtocolException(
    message: String,
    val kind: SabrFailureKind = SabrFailureKind.PROTOCOL,
    val httpStatusCode: Int? = null,
) : IllegalStateException(message)

fun Throwable.findSabrProtocolException(): SabrProtocolException? {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is SabrProtocolException) return current
        current = current.cause
    }
    return null
}
