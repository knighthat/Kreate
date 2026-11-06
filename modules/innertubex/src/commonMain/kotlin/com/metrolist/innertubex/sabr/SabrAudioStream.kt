package com.metrolist.innertubex.sabr

import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.d
import com.metrolist.innertubex.e
import com.metrolist.innertubex.i
import com.metrolist.innertubex.w
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseRedirectEvent
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlin.time.TimeSource

/** Fixed-format, audio-only SABR transport. */
@ExperimentalSabrApi
class SabrAudioStream(
    private val httpClient: HttpClient,
    private val bootstrap: SabrBootstrap,
    private val maxRequestsWithoutProgress: Int = 3,
    private val playbackPositionMs: (() -> Long)? = null,
    private val onResponse: ((SabrResponseDiagnostics) -> Unit)? = null,
    private val requestRetryDelay: suspend (Long) -> Unit = { delay(it) },
    private val initialPlayerTimeMs: Long = 0,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
) {
    private val delegate =
        SabrMediaStream(
            httpClient = httpClient,
            bootstrap = bootstrap,
            mediaType = SabrMediaType.AUDIO,
            maxRequestsWithoutProgress = maxRequestsWithoutProgress,
            playbackPositionMs = playbackPositionMs,
            onResponse = onResponse,
            requestRetryDelay = requestRetryDelay,
            initialPlayerTimeMs = initialPlayerTimeMs,
            logger = logger,
        )

    fun bytes(): Flow<ByteArray> = chunks().map { it.data }

    fun chunks(): Flow<SabrChunk> = delegate.chunks()
}

/** Fixed-format SABR video transport synchronized to an external playback clock. */
@ExperimentalSabrApi
class SabrVideoStream(
    httpClient: HttpClient,
    bootstrap: SabrBootstrap,
    maxRequestsWithoutProgress: Int = 3,
    playbackPositionMs: (() -> Long)? = null,
    onResponse: ((SabrResponseDiagnostics) -> Unit)? = null,
    requestRetryDelay: suspend (Long) -> Unit = { delay(it) },
    initialPlayerTimeMs: Long = 0,
    logger: InnerTubeLogger = InnerTubeLogger.NONE,
) {
    private val delegate =
        SabrMediaStream(
            httpClient = httpClient,
            bootstrap = bootstrap,
            mediaType = SabrMediaType.VIDEO,
            maxRequestsWithoutProgress = maxRequestsWithoutProgress,
            playbackPositionMs = playbackPositionMs,
            onResponse = onResponse,
            requestRetryDelay = requestRetryDelay,
            initialPlayerTimeMs = initialPlayerTimeMs,
            logger = logger,
        )

    fun bytes(): Flow<ByteArray> = chunks().map { it.data }

    fun chunks(): Flow<SabrChunk> = delegate.chunks()
}

private class SabrMediaStream(
    private val httpClient: HttpClient,
    private val bootstrap: SabrBootstrap,
    private val mediaType: SabrMediaType,
    private val maxRequestsWithoutProgress: Int,
    private val playbackPositionMs: (() -> Long)?,
    private val onResponse: ((SabrResponseDiagnostics) -> Unit)?,
    private val requestRetryDelay: suspend (Long) -> Unit,
    private val initialPlayerTimeMs: Long,
    private val logger: InnerTubeLogger,
) {
    private val selectedFormat =
        when (mediaType) {
            SabrMediaType.AUDIO -> bootstrap.audioFormat
            SabrMediaType.VIDEO -> requireNotNull(bootstrap.selectedVideoFormat) { "SABR video format is not selected" }
        }
    private val mediaLabel = mediaType.name.lowercase()
    private val tag = if (mediaType == SabrMediaType.AUDIO) "SabrAudioStream" else "SabrVideoStream"
    private val expectedContentLengthBytes =
        when (mediaType) {
            SabrMediaType.AUDIO -> bootstrap.contentLengthBytes
            SabrMediaType.VIDEO -> bootstrap.selectedVideoContentLengthBytes
        }

    fun chunks(): Flow<SabrChunk> {
        var requestNumber = 0
        var lastResponseStatus: Int? = null
        var lastResponseContentType: String? = null
        var lastResponseBytes = 0L
        var lastRequestPlayerTimeMs = initialPlayerTimeMs
        var lastObservedPlaybackPositionMs: Long? = null
        return channelFlow {
            logger.i(
                tag,
                buildString {
                    append("Opened SABR $mediaLabel stream itag=${selectedFormat.itag}")
                    append(", duration=${bootstrap.durationMs}ms")
                    append(", poToken=${if (bootstrap.poToken == null) "missing" else "present"}")
                    append(", clientVersion=${bootstrap.clientVersion}.")
                },
                mediaId = bootstrap.videoId,
                details = bootstrap.logDetails(selectedFormat, mediaType),
            )
            var streamingUrl = requireAllowedSabrUrl(bootstrap.serverAbrStreamingUrl)
            var playerTimeMs = initialPlayerTimeMs.coerceIn(0L, (bootstrap.durationMs - 1L).coerceAtLeast(0L))
            var initialized = false
            var initialization: SabrFormatInitialization? = null
            var initSegmentEmitted = false
            var playbackCookie: ByteArray? = null
            var targetAudioReadaheadMs = 0L
            var maxTimeSinceLastRequestMs = 0L
            var minAudioReadaheadMs = 0L
            var bufferedRanges = emptyList<SabrBufferedRange>()
            var firstBufferedSegment: SabrMediaHeader? = null
            var backoffTimeMs = 0L
            var requestsWithoutProgress = 0
            var ended = false
            var firstSequenceNumber: Int? = null
            var lastSequenceNumber: Int? = null
            var seekStartEstablished = false
            var consecutiveTransientFailures = 0
            var transientRetryCount = 0
            var cumulativeMediaBytes = 0L
            var cumulativeStreamBytes = 0L
            var cumulativeSegmentCount = 0
            var protectionPending = false
            var protectionRetryCount = 0
            var protectionRetryLimit: Int? = null
            var omittedAllowancePending = false
            var omittedProtectionPendingCount = 0
            val contexts = mutableMapOf<Int, SabrContext>()
            val activeContextTypes = mutableSetOf<Int>()
            var retainedContextBytes = 0L

            while (
                !ended &&
                initialization?.endSegmentNumber?.let { end ->
                    lastSequenceNumber?.let { sequence -> sequence >= end } == true
                } != true &&
                playerTimeMs < expectedDurationMs(initialization)
            ) {
                if (requestNumber >= MAX_REQUEST_COUNT) {
                    throw SabrProtocolException("SABR exceeded $MAX_REQUEST_COUNT requests")
                }
                if (playbackPositionMs != null && targetAudioReadaheadMs > 0 && playerTimeMs > 0) {
                    awaitReadaheadCapacity(
                        downloadedPositionMs = playerTimeMs,
                        targetAudioReadaheadMs = targetAudioReadaheadMs,
                        maxTimeSinceLastRequestMs = maxTimeSinceLastRequestMs,
                    )
                }
                if (backoffTimeMs > 0) delay(backoffTimeMs.coerceAtMost(MAX_BACKOFF_MS))
                val currentRequestNumber = requestNumber
                val observedPlaybackPositionMs = playbackPositionMs?.invoke()?.coerceAtLeast(0L)
                val requestPlayerTimeMs = playerTimeMs
                lastRequestPlayerTimeMs = requestPlayerTimeMs
                lastObservedPlaybackPositionMs = observedPlaybackPositionMs
                val request =
                    SabrProtoCodec.encodeRequest(
                        bootstrap = bootstrap,
                        playerTimeMs = requestPlayerTimeMs,
                        initialized = initialized,
                        bufferedRanges = bufferedRanges,
                        playbackCookie = playbackCookie,
                        contexts = contexts.values,
                        activeContextTypes = activeContextTypes,
                        mediaType = mediaType,
                    )
                val retryResponse =
                    httpClient.withRedirectsDisabled {
                        preparePost(withRequestNumber(streamingUrl, requestNumber++)) {
                            attributes.put(SABR_REQUEST_ATTRIBUTE, Unit)
                            contentType(ContentType.parse(CONTENT_TYPE_PROTOBUF))
                            header(HttpHeaders.Accept, CONTENT_TYPE_UMP)
                            header(HttpHeaders.AcceptEncoding, "identity")
                            bootstrap.requestUserAgent?.let { header(HttpHeaders.UserAgent, it) }
                            bootstrap.requestOrigin?.let {
                                header(HttpHeaders.Origin, it)
                                header(HttpHeaders.Referrer, "$it/")
                            }
                            setBody(request)
                        }.execute { response ->
                            lastResponseStatus = response.status.value
                            lastResponseContentType = response.headers[HttpHeaders.ContentType]
                            lastResponseBytes = 0L
                            val channel = response.bodyAsChannel()
                            if (!response.status.isSuccess()) {
                                val responseDetails =
                                    listOfNotNull(
                                        response.headers[HttpHeaders.ContentType]?.let { "type=$it" },
                                        response.headers[HttpHeaders.ContentLength]?.let { "length=$it" },
                                        response.headers[HttpHeaders.Allow]?.let { "allow=$it" },
                                        response.headers[HttpHeaders.Server]?.let { "server=$it" },
                                    ).joinToString()
                                channel.cancel(null)
                                if (
                                    response.status.value in TRANSIENT_HTTP_STATUSES &&
                                    consecutiveTransientFailures < MAX_TRANSIENT_RETRIES
                                ) {
                                    consecutiveTransientFailures++
                                    transientRetryCount++
                                    logger.w(
                                        tag,
                                        buildString {
                                            append("SABR HTTP ${response.status.value} is transient; ")
                                            append("retry $consecutiveTransientFailures/$MAX_TRANSIENT_RETRIES.")
                                        },
                                        mediaId = bootstrap.videoId,
                                        details =
                                            buildMap {
                                                put("http", response.status.value.toString())
                                                put("transientRetry", "$consecutiveTransientFailures/$MAX_TRANSIENT_RETRIES")
                                                if (responseDetails.isNotEmpty()) put("responseHeaders", responseDetails)
                                                putAll(bootstrap.logDetails(selectedFormat, mediaType))
                                            },
                                    )
                                    val retryDelayMs =
                                        response.headers[HttpHeaders.RetryAfter]
                                            ?.toLongOrNull()
                                            ?.times(1_000L)
                                            ?.coerceIn(RETRY_BASE_DELAY_MS, RETRY_MAX_DELAY_MS)
                                            ?: (RETRY_BASE_DELAY_MS shl (consecutiveTransientFailures - 1))
                                    requestRetryDelay(retryDelayMs)
                                    return@execute true
                                }
                                throw SabrProtocolException(
                                    "SABR request failed with HTTP ${response.status.value}" +
                                        responseDetails.takeIf(String::isNotEmpty)?.let { " ($it)" }.orEmpty(),
                                    httpStatusCode = response.status.value,
                                )
                            }
                            val responseContentType = response.headers[HttpHeaders.ContentType].orEmpty()
                            if (!responseContentType.startsWith(CONTENT_TYPE_UMP)) {
                                channel.cancel(null)
                                throw SabrProtocolException("Unexpected SABR content type")
                            }
                            consecutiveTransientFailures = 0

                            val processor =
                                SabrUmpProcessor(
                                    shouldCollectMedia = { header ->
                                        header.formatId.matches(selectedFormat) &&
                                            (header.videoId == null || header.videoId == bootstrap.videoId)
                                    },
                                )
                            var responseMediaBytes = 0L
                            var responseBytes = 0L
                            var networkReadMs = 0L
                            var responseInitializationReceived = false
                            var responseInitSegmentCount = 0
                            val selectedHeaderSequences = mutableListOf<Int>()
                            val selectedHeaderStartRanges = mutableListOf<Long>()
                            val selectedHeaderContentLengths = mutableListOf<Long>()
                            var unselectedHeaderCount = 0
                            var responseProtectionStatus: Int? = null
                            var responseProtectionMaxRetries: Int? = null
                            val pendingMediaBySequence = linkedMapOf<Int, SabrSegment>()
                            var firstNewSegmentHeader: SabrMediaHeader? = null
                            var lastNewSegmentHeader: SabrMediaHeader? = null
                            var newSegmentsBytes = 0L
                            var newSegmentCount = 0

                            suspend fun emitMediaSegment(segment: SabrSegment) {
                                send(segment.toChunk())
                                if (firstSequenceNumber == null) firstSequenceNumber = segment.header.sequenceNumber
                                lastSequenceNumber = segment.header.sequenceNumber
                                playerTimeMs = maxOf(playerTimeMs, segment.checkedEndTimeMs())
                                cumulativeMediaBytes += segment.data.size
                                cumulativeStreamBytes += segment.data.size
                                cumulativeSegmentCount++
                                if (firstNewSegmentHeader == null) firstNewSegmentHeader = segment.header
                                lastNewSegmentHeader = segment.header
                                newSegmentsBytes += segment.data.size
                                newSegmentCount++
                            }

                            suspend fun emitReadyMediaSegments(
                                initialSeekSequence: Int? = null,
                                establishFromMinimum: Boolean = false,
                            ) {
                                if (lastSequenceNumber == null) {
                                    initialSeekSequence?.let { seekSequence ->
                                        pendingMediaBySequence.keys
                                            .filter { it < seekSequence }
                                            .forEach(pendingMediaBySequence::remove)
                                    }
                                    val firstSequence =
                                        when {
                                            initialSeekSequence != null -> initialSeekSequence

                                            pendingMediaBySequence.containsKey(0) &&
                                                (requestPlayerTimeMs <= 0L || establishFromMinimum) -> 0

                                            establishFromMinimum -> pendingMediaBySequence.keys.minOrNull()

                                            else -> null
                                        } ?: return
                                    emitMediaSegment(checkNotNull(pendingMediaBySequence.remove(firstSequence)))
                                }

                                while (true) {
                                    val expectedSequence = checkNotNull(lastSequenceNumber) + 1
                                    val segment = pendingMediaBySequence.remove(expectedSequence) ?: break
                                    emitMediaSegment(segment)
                                }
                            }

                            fun initialSeekSequence(): Int? {
                                if (firstSequenceNumber != null || requestPlayerTimeMs <= 0L) return null
                                return pendingMediaBySequence.values
                                    .firstOrNull { pending ->
                                        pending.header.startMs <= requestPlayerTimeMs &&
                                            requestPlayerTimeMs < pending.checkedEndTimeMs()
                                    }?.header
                                    ?.sequenceNumber
                            }

                            suspend fun emitAvailableMediaSegments() {
                                val establishSeekSequence = initialSeekSequence()
                                if (establishSeekSequence != null) seekStartEstablished = true
                                emitReadyMediaSegments(initialSeekSequence = establishSeekSequence)
                            }

                            suspend fun processEvent(event: SabrEvent) {
                                when (event) {
                                    is SabrEvent.MediaHeader -> {
                                        if (
                                            !event.header.isInitSegment &&
                                            event.header.formatId.matches(selectedFormat) &&
                                            (event.header.videoId == null || event.header.videoId == bootstrap.videoId)
                                        ) {
                                            selectedHeaderSequences += event.header.sequenceNumber
                                            selectedHeaderStartRanges += event.header.startRange
                                            selectedHeaderContentLengths += event.header.contentLength
                                        } else if (!event.header.isInitSegment) {
                                            unselectedHeaderCount++
                                        }
                                    }

                                    is SabrEvent.Segment -> {
                                        val segment = event.segment
                                        responseMediaBytes += segment.data.size
                                        if (responseMediaBytes > MAX_RESPONSE_MEDIA_BYTES) {
                                            throw SabrProtocolException("SABR response media exceeded the memory limit")
                                        }
                                        if (segment.header.isInitSegment) {
                                            responseInitializationReceived = true
                                            responseInitSegmentCount++
                                            if (responseInitSegmentCount > 1) {
                                                throw SabrProtocolException("SABR returned duplicate initialization segments")
                                            }
                                            if (!initSegmentEmitted) {
                                                send(segment.toChunk())
                                                initSegmentEmitted = true
                                                cumulativeStreamBytes += segment.data.size
                                                emitAvailableMediaSegments()
                                            }
                                        } else {
                                            val sequenceNumber = segment.header.sequenceNumber
                                            val precedesEstablishedSeek =
                                                seekStartEstablished &&
                                                    firstSequenceNumber?.let { sequenceNumber < it } == true
                                            val alreadyEmitted =
                                                precedesEstablishedSeek ||
                                                    lastSequenceNumber?.let { last ->
                                                        sequenceNumber >= checkNotNull(firstSequenceNumber) && sequenceNumber <= last
                                                    } == true
                                            if (!alreadyEmitted) {
                                                if (pendingMediaBySequence.put(sequenceNumber, segment) != null) {
                                                    throw SabrProtocolException("SABR returned duplicate segment $sequenceNumber")
                                                }
                                                if (initSegmentEmitted) {
                                                    emitAvailableMediaSegments()
                                                }
                                            }
                                        }
                                    }

                                    is SabrEvent.FormatInitialized -> {
                                        val value = event.initialization
                                        if (value?.formatId?.matches(selectedFormat) == true) {
                                            initialized = true
                                            initialization = value
                                        }
                                    }

                                    is SabrEvent.NextRequestPolicy -> {
                                        targetAudioReadaheadMs = event.targetAudioReadaheadMs
                                        maxTimeSinceLastRequestMs = event.maxTimeSinceLastRequestMs
                                        backoffTimeMs = event.backoffTimeMs
                                        minAudioReadaheadMs = event.minAudioReadaheadMs
                                        if ((event.playbackCookie?.size ?: 0) > MAX_PLAYBACK_COOKIE_BYTES) {
                                            throw SabrProtocolException("SABR playback cookie exceeded the memory limit")
                                        }
                                        playbackCookie = event.playbackCookie
                                    }

                                    is SabrEvent.Redirect -> {
                                        streamingUrl = requireAllowedSabrUrl(event.url)
                                    }

                                    is SabrEvent.ContextUpdate -> {
                                        val existing = contexts[event.context.type]
                                        if (event.context.writePolicy != KEEP_EXISTING || existing == null) {
                                            if (event.context.value.size > MAX_CONTEXT_VALUE_BYTES) {
                                                throw SabrProtocolException("SABR context value exceeded the memory limit")
                                            }
                                            if (existing == null && contexts.size >= MAX_CONTEXT_COUNT) {
                                                throw SabrProtocolException("SABR context count exceeded the memory limit")
                                            }
                                            val updatedContextBytes =
                                                retainedContextBytes - (existing?.value?.size ?: 0) + event.context.value.size
                                            if (updatedContextBytes > MAX_CONTEXT_BYTES) {
                                                throw SabrProtocolException("SABR contexts exceeded the memory limit")
                                            }
                                            contexts[event.context.type] = event.context
                                            retainedContextBytes = updatedContextBytes
                                            if (event.context.sendByDefault) {
                                                activeContextTypes += event.context.type
                                            }
                                        }
                                    }

                                    is SabrEvent.ContextSendingPolicy -> {
                                        if (
                                            event.start.size > MAX_CONTEXT_COUNT ||
                                            event.stop.size > MAX_CONTEXT_COUNT ||
                                            event.discard.size > MAX_CONTEXT_COUNT
                                        ) {
                                            throw SabrProtocolException("SABR context policy exceeded the type limit")
                                        }
                                        activeContextTypes += event.start
                                        activeContextTypes -= event.stop
                                        event.discard.forEach { type ->
                                            activeContextTypes -= type
                                            contexts.remove(type)?.let { retainedContextBytes -= it.value.size }
                                        }
                                        if (activeContextTypes.size > MAX_CONTEXT_COUNT) {
                                            throw SabrProtocolException("SABR active context count exceeded the memory limit")
                                        }
                                    }

                                    is SabrEvent.StreamProtectionStatus -> {
                                        responseProtectionStatus = event.status
                                        responseProtectionMaxRetries = event.maxRetries.takeIf { it >= 0 }
                                    }

                                    is SabrEvent.Error -> {
                                        throw SabrProtocolException("SABR error ${event.type.orEmpty()} (${event.code})")
                                    }

                                    SabrEvent.ReloadPlayerResponse -> {
                                        throw SabrProtocolException(
                                            "SABR requested a fresh player response",
                                            SabrFailureKind.RELOAD_PLAYER,
                                        )
                                    }

                                    SabrEvent.EndOfTrack -> {
                                        ended = true
                                    }
                                }
                            }

                            val readBuffer = ByteArray(64 * 1024)
                            try {
                                while (true) {
                                    // Only time spent waiting on the network counts; emitting can block on player readahead.
                                    val readStart = TimeSource.Monotonic.markNow()
                                    val count = channel.readAvailable(readBuffer, 0, readBuffer.size)
                                    networkReadMs += readStart.elapsedNow().inWholeMilliseconds
                                    if (count == -1) break
                                    if (count == 0) continue
                                    responseBytes += count
                                    lastResponseBytes = responseBytes
                                    processor.feed(readBuffer, count).forEach { event -> processEvent(event) }
                                }
                                processor.finish()
                                emitReadyMediaSegments(establishFromMinimum = true)
                                if (pendingMediaBySequence.isNotEmpty()) {
                                    throw SabrProtocolException(
                                        "SABR segment sequence gap: expected ${checkNotNull(lastSequenceNumber) + 1}, " +
                                            "received ${pendingMediaBySequence.keys.minOrNull()}",
                                    )
                                }
                            } catch (cancelled: CancellationException) {
                                channel.cancel(cancelled)
                                throw cancelled
                            } catch (error: Throwable) {
                                channel.cancel(error)
                                throw error
                            }

                            if (newSegmentCount > 0) {
                                requestsWithoutProgress = 0
                                if (firstBufferedSegment == null) firstBufferedSegment = firstNewSegmentHeader
                                val first = checkNotNull(firstBufferedSegment)
                                val last = checkNotNull(lastNewSegmentHeader)
                                bufferedRanges =
                                    listOf(
                                        SabrBufferedRange(
                                            formatId = selectedFormat,
                                            startTimeMs = first.startMs,
                                            // End timestamps have been checked; durations can have rounding gaps/overlap.
                                            durationMs = (last.startMs + last.durationMs - first.startMs).coerceAtLeast(0L),
                                            startSegmentIndex = first.sequenceNumber,
                                            endSegmentIndex = last.sequenceNumber,
                                        ),
                                    )
                            }

                            when {
                                responseProtectionStatus?.let { it >= PROTECTION_ATTESTATION_REQUIRED } == true -> {
                                    protectionPending = true
                                }

                                responseProtectionStatus == PROTECTION_ATTESTATION_PENDING -> {
                                    protectionPending = true
                                    omittedAllowancePending = responseProtectionMaxRetries == null
                                    responseProtectionMaxRetries
                                        ?.let { protectionRetryLimit = it }
                                }

                                responseProtectionStatus == PROTECTION_ATTESTATION_ACCEPTED -> {
                                    protectionPending = false
                                    protectionRetryCount = 0
                                    protectionRetryLimit = null
                                    omittedAllowancePending = false
                                }
                            }
                            if (protectionPending && responseProtectionStatus?.let { it >= PROTECTION_ATTESTATION_REQUIRED } != true) {
                                protectionRetryCount++
                                if (omittedAllowancePending) omittedProtectionPendingCount++
                            }

                            val diagnostics =
                                SabrResponseDiagnostics(
                                    requestNumber = currentRequestNumber,
                                    requestPlayerTimeMs = requestPlayerTimeMs,
                                    observedPlaybackPositionMs = observedPlaybackPositionMs,
                                    bufferedRanges = bufferedRanges,
                                    receivedSequences = selectedHeaderSequences,
                                    receivedStartRanges = selectedHeaderStartRanges,
                                    receivedContentLengths = selectedHeaderContentLengths,
                                    initializationReceived = responseInitializationReceived,
                                    playbackCookieSize = playbackCookie?.size ?: 0,
                                    contextTypes = contexts.keys.toSet(),
                                    activeContextTypes = activeContextTypes.toSet(),
                                    targetAudioReadaheadMs = targetAudioReadaheadMs,
                                    minAudioReadaheadMs = minAudioReadaheadMs,
                                    maxTimeSinceLastRequestMs = maxTimeSinceLastRequestMs,
                                    backoffTimeMs = backoffTimeMs,
                                    responseBytes = responseBytes,
                                    networkReadMs = networkReadMs,
                                    httpStatus = response.status.value,
                                    selectedMediaBytes = newSegmentsBytes,
                                    cumulativeMediaBytes = cumulativeMediaBytes,
                                    selectedSegmentCount = newSegmentCount,
                                    cumulativeSegmentCount = cumulativeSegmentCount,
                                    transientRetryCount = transientRetryCount,
                                    noProgressRequestCount = requestsWithoutProgress,
                                    protectionStatus = responseProtectionStatus,
                                    protectionMaxRetries = responseProtectionMaxRetries,
                                )
                            onResponse?.invoke(diagnostics)
                            logResponse(diagnostics)

                            // ponytail: allow at most five responses while an omitted allowance is pending per stream;
                            // refine only if observed protocol behavior warrants a larger budget.
                            val protectionRetriesExhausted =
                                protectionRetryLimit?.let { protectionRetryCount > it } == true ||
                                    omittedProtectionPendingCount > MAX_OMITTED_PROTECTION_PENDING
                            val protectionRequired =
                                responseProtectionStatus?.let { it >= PROTECTION_ATTESTATION_REQUIRED } == true
                            if (protectionPending && (protectionRequired || newSegmentCount == 0 || protectionRetriesExhausted)) {
                                throw SabrProtocolException(
                                    "SABR stream protection requires attestation " +
                                        "(status ${responseProtectionStatus ?: PROTECTION_ATTESTATION_PENDING}, " +
                                        "retry=$protectionRetryCount/${protectionRetryLimit ?: "unspecified"})",
                                    SabrFailureKind.ATTESTATION_REQUIRED,
                                )
                            }

                            if (newSegmentCount == 0 && !ended) {
                                val playbackPosition = playbackPositionMs?.invoke()?.coerceAtLeast(0L)
                                val hasSafeReadahead = playbackPosition != null && playerTimeMs - playbackPosition > STARVATION_TOLERANCE_MS
                                if (hasSafeReadahead) {
                                    requestsWithoutProgress = 0
                                    awaitPlaybackProgress(playbackPosition, maxTimeSinceLastRequestMs)
                                } else if (++requestsWithoutProgress >= maxRequestsWithoutProgress) {
                                    throw SabrProtocolException(
                                        "SABR made no $mediaLabel progress for $requestsWithoutProgress requests " +
                                            "(segment=${lastSequenceNumber ?: "none"}/${initialization?.endSegmentNumber ?: "unknown"}, " +
                                            "time=$playerTimeMs/${expectedDurationMs(initialization)}ms, initialized=$initialized, " +
                                            "readahead=$minAudioReadaheadMs..$targetAudioReadaheadMs, " +
                                            "maxRequestInterval=$maxTimeSinceLastRequestMs, backoff=$backoffTimeMs, " +
                                            "headers=${selectedHeaderSequences.joinToString()}/$unselectedHeaderCount)",
                                    )
                                }
                            }
                            false
                        }
                    }
                if (retryResponse) continue
            }

            if (protectionPending) {
                throw SabrProtocolException("SABR stream protection remains pending", SabrFailureKind.ATTESTATION_REQUIRED)
            }
            validateCompleteStream(
                initialization = initialization,
                initSegmentEmitted = initSegmentEmitted,
                lastSequenceNumber = lastSequenceNumber,
                downloadedDurationMs = playerTimeMs,
                emittedBytes = cumulativeStreamBytes,
            )
            logger.i(
                tag,
                "SABR $mediaLabel stream finished after $requestNumber UMP requests, " +
                    "$cumulativeSegmentCount segments, $cumulativeMediaBytes bytes.",
                mediaId = bootstrap.videoId,
                details =
                    mapOf(
                        "requestCount" to requestNumber.toString(),
                        "segments" to cumulativeSegmentCount.toString(),
                        "mediaBytes" to cumulativeMediaBytes.toString(),
                        "transientRetries" to transientRetryCount.toString(),
                        "protectionRetries" to protectionRetryCount.toString(),
                    ) + bootstrap.logDetails(selectedFormat, mediaType),
            )
        }.buffer(0).catch { error ->
            if (error is CancellationException) throw error
            onResponse?.invoke(
                SabrResponseDiagnostics(
                    requestNumber = requestNumber,
                    requestPlayerTimeMs = lastRequestPlayerTimeMs,
                    observedPlaybackPositionMs = lastObservedPlaybackPositionMs,
                    bufferedRanges = emptyList(),
                    receivedSequences = emptyList(),
                    receivedStartRanges = emptyList(),
                    receivedContentLengths = emptyList(),
                    initializationReceived = false,
                    playbackCookieSize = 0,
                    contextTypes = emptySet(),
                    activeContextTypes = emptySet(),
                    targetAudioReadaheadMs = 0L,
                    minAudioReadaheadMs = 0L,
                    maxTimeSinceLastRequestMs = 0L,
                    backoffTimeMs = 0L,
                    responseBytes = lastResponseBytes,
                    httpStatus = lastResponseStatus,
                    contentType = lastResponseContentType,
                    failureCategory = error.findSabrProtocolException()?.kind?.name ?: error::class.simpleName,
                    failureMessage = "SABR stream failed",
                ),
            )
            logger.e(
                tag,
                "SABR $mediaLabel stream failed",
                mediaId = bootstrap.videoId,
                details =
                    bootstrap.logDetails(selectedFormat, mediaType) +
                        buildMap {
                            put("failureType", error::class.simpleName ?: "Throwable")
                            error.findSabrProtocolException()?.let { failure ->
                                put("sabrKind", failure.kind.name)
                                failure.httpStatusCode?.let { put("httpStatus", it.toString()) }
                            }
                        },
            )
            throw error
        }
    }

    private suspend fun awaitPlaybackProgress(
        previousPositionMs: Long,
        maxTimeSinceLastRequestMs: Long,
    ) {
        val waitLimitMs =
            maxTimeSinceLastRequestMs
                .takeIf { it > 0 }
                ?.coerceAtMost(MAX_HEARTBEAT_INTERVAL_MS)
                ?: DEFAULT_HEARTBEAT_INTERVAL_MS
        var waitedMs = 0L
        while (
            playbackPositionMs?.invoke()?.coerceAtLeast(0L) == previousPositionMs &&
            waitedMs < waitLimitMs
        ) {
            delay(PLAYBACK_POLL_INTERVAL_MS)
            waitedMs += PLAYBACK_POLL_INTERVAL_MS
        }
    }

    private fun logResponse(diagnostics: SabrResponseDiagnostics) {
        val notable =
            (diagnostics.protectionStatus ?: 0) >= PROTECTION_ATTESTATION_PENDING ||
                diagnostics.selectedSegmentCount == 0 ||
                diagnostics.transientRetryCount > 0 ||
                diagnostics.noProgressRequestCount > 0 ||
                diagnostics.backoffTimeMs > 0L
        val message =
            buildString {
                append("SABR UMP request #${diagnostics.requestNumber}")
                append(": playerTime=${diagnostics.requestPlayerTimeMs}ms")
                append(", segments=${diagnostics.selectedSegmentCount}")
                append(" (total ${diagnostics.cumulativeSegmentCount})")
                append(", mediaBytes=${diagnostics.selectedMediaBytes}")
                append(", responseBytes=${diagnostics.responseBytes}")
                if (diagnostics.receivedSequences.isNotEmpty()) {
                    append(", sequences=${diagnostics.receivedSequences.joinToString()}")
                }
                diagnostics.protectionStatus?.let { append(", protection=$it") }
                if (diagnostics.backoffTimeMs > 0L) append(", backoff=${diagnostics.backoffTimeMs}ms")
                if (diagnostics.noProgressRequestCount > 0) {
                    append(", noProgressRequests=${diagnostics.noProgressRequestCount}")
                }
            }
        if (notable) {
            logger.w(tag, message, details = diagnostics.logDetails())
        } else {
            logger.d(tag, message, details = diagnostics.logDetails())
        }
    }

    private suspend fun awaitReadaheadCapacity(
        downloadedPositionMs: Long,
        targetAudioReadaheadMs: Long,
        maxTimeSinceLastRequestMs: Long,
    ) {
        val waitLimitMs =
            maxTimeSinceLastRequestMs
                .takeIf { it > 0 }
                ?.coerceAtMost(MAX_HEARTBEAT_INTERVAL_MS)
                ?: DEFAULT_HEARTBEAT_INTERVAL_MS
        var waitedMs = 0L
        while (waitedMs < waitLimitMs) {
            val playbackPosition = playbackPositionMs?.invoke()?.coerceAtLeast(0L) ?: return
            if (downloadedPositionMs - playbackPosition <= targetAudioReadaheadMs) return
            delay(PLAYBACK_POLL_INTERVAL_MS)
            waitedMs += PLAYBACK_POLL_INTERVAL_MS
        }
    }

    private fun validateCompleteStream(
        initialization: SabrFormatInitialization?,
        initSegmentEmitted: Boolean,
        lastSequenceNumber: Int?,
        downloadedDurationMs: Long,
        emittedBytes: Long,
    ) {
        if (!initSegmentEmitted) throw SabrProtocolException("SABR stream has no initialization segment")
        val finalSequenceNumber = lastSequenceNumber ?: throw SabrProtocolException("SABR stream has no media segments")
        initialization?.endSegmentNumber?.let { expected ->
            if (finalSequenceNumber != expected) {
                throw SabrProtocolException(
                    "Incomplete SABR stream: last segment is $finalSequenceNumber, expected $expected",
                )
            }
        }
        val expectedDurationMs = expectedDurationMs(initialization)
        val toleranceMs = maxOf(MIN_DURATION_TOLERANCE_MS, expectedDurationMs / 100)
        val durationDifference =
            if (downloadedDurationMs >= expectedDurationMs) {
                downloadedDurationMs - expectedDurationMs
            } else {
                expectedDurationMs - downloadedDurationMs
            }
        if (durationDifference > toleranceMs) {
            throw SabrProtocolException(
                "Incomplete SABR stream duration: downloaded ${downloadedDurationMs}ms, expected ${expectedDurationMs}ms",
            )
        }
        if (initialPlayerTimeMs == 0L) {
            expectedContentLengthBytes?.takeIf { it > 0L }?.let { expectedBytes ->
                if (emittedBytes != expectedBytes) {
                    throw SabrProtocolException(
                        "Incomplete SABR stream bytes: emitted $emittedBytes, expected $expectedBytes",
                    )
                }
            }
        }
    }

    private fun expectedDurationMs(initialization: SabrFormatInitialization?): Long =
        initialization?.durationMs?.takeIf { it > 0 } ?: bootstrap.durationMs

    private fun withRequestNumber(
        url: String,
        requestNumber: Int,
    ): String =
        URLBuilder(url)
            .apply { parameters["rn"] = requestNumber.toString() }
            .buildString()

    private fun SabrSegment.toChunk() =
        SabrChunk(
            data = data,
            startRange = header.startRange,
            isInitialization = header.isInitSegment,
            sequenceNumber = header.sequenceNumber.takeUnless { header.isInitSegment },
            startMs = header.startMs.takeUnless { header.isInitSegment },
            durationMs = header.durationMs.takeUnless { header.isInitSegment },
        )

    private fun SabrSegment.checkedEndTimeMs(): Long {
        val startMs = header.startMs
        val durationMs = header.durationMs
        if (startMs < 0L || durationMs < 0L || startMs > Long.MAX_VALUE - durationMs) {
            throw SabrProtocolException("SABR segment has an invalid time range")
        }
        return startMs + durationMs
    }

    private companion object {
        const val CONTENT_TYPE_PROTOBUF = "application/x-protobuf"
        const val CONTENT_TYPE_UMP = "application/vnd.yt-ump"
        const val KEEP_EXISTING = 2
        const val PROTECTION_ATTESTATION_ACCEPTED = 1
        const val PROTECTION_ATTESTATION_PENDING = 2
        const val PROTECTION_ATTESTATION_REQUIRED = 3
        const val MAX_OMITTED_PROTECTION_PENDING = 5
        const val MAX_REQUEST_COUNT = 10_000
        const val MAX_BACKOFF_MS = 30_000L
        const val MAX_RESPONSE_MEDIA_BYTES = 64L * 1024 * 1024
        const val MAX_CONTEXT_COUNT = 64
        const val MAX_CONTEXT_VALUE_BYTES = 64 * 1024
        const val MAX_CONTEXT_BYTES = 256L * 1024
        const val MAX_PLAYBACK_COOKIE_BYTES = 64 * 1024
        const val MIN_DURATION_TOLERANCE_MS = 2_000L
        const val STARVATION_TOLERANCE_MS = 2_000L
        const val PLAYBACK_POLL_INTERVAL_MS = 250L
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 60_000L
        const val MAX_HEARTBEAT_INTERVAL_MS = 60_000L
        const val MAX_TRANSIENT_RETRIES = 2
        const val RETRY_BASE_DELAY_MS = 250L
        const val RETRY_MAX_DELAY_MS = 5_000L
        val TRANSIENT_HTTP_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}

private suspend fun <T> HttpClient.withRedirectsDisabled(block: suspend HttpClient.() -> T): T {
    val rejectRedirect: (HttpResponse) -> Unit = { response ->
        val requestAttributes = response.call.request.attributes
        if (requestAttributes.contains(SABR_REQUEST_ATTRIBUTE)) {
            throw SabrProtocolException("SABR HTTP redirects are not allowed")
        }
    }
    val subscription = monitor.subscribe(HttpResponseRedirectEvent, rejectRedirect)
    return try {
        block()
    } finally {
        subscription.dispose()
    }
}

private val SABR_REQUEST_ATTRIBUTE = AttributeKey<Unit>("SabrRequest")

private fun SabrBootstrap.logDetails(
    selectedFormat: SabrFormatId,
    mediaType: SabrMediaType,
): Map<String, String> =
    buildMap {
        put("sabrMediaType", mediaType.name)
        put("sabrItag", selectedFormat.itag.toString())
        put("sabrLastModified", selectedFormat.lastModified.toString())
        selectedFormat.xtags?.let { put("sabrXtags", it) }
        put("sabrDurationMs", durationMs.toString())
        contentLengthBytes?.let { put("sabrContentLength", it.toString()) }
        put("sabrMime", mimeType)
        put("sabrClientId", clientName.toString())
        put("sabrClientVersion", clientVersion)
        put("sabrDrc", isDrc.toString())
        put("sabrPoToken", if (poToken == null) "missing" else "present")
        put("sabrUstreamerConfig", "${videoPlaybackUstreamerConfig.size}bytes")
        put("sabrDiscardVideoItag", discardVideoFormat.itag.toString())
        put("sabrDiscardVideoHeight", discardVideoHeight.toString())
        audioTrackId?.takeIf { it.isNotBlank() }?.let { put("sabrAudioTrackId", it) }
    }

private fun SabrResponseDiagnostics.logDetails(): Map<String, String> =
    buildMap {
        put("requestNumber", requestNumber.toString())
        put("playerTimeMs", requestPlayerTimeMs.toString())
        observedPlaybackPositionMs?.let { put("playbackPositionMs", it.toString()) }
        put("segments", selectedSegmentCount.toString())
        put("cumulativeSegments", cumulativeSegmentCount.toString())
        put("responseBytes", responseBytes.toString())
        put("mediaBytes", selectedMediaBytes.toString())
        put("cumulativeMediaBytes", cumulativeMediaBytes.toString())
        put("initializationReceived", initializationReceived.toString())
        put("playbackCookieSize", playbackCookieSize.toString())
        put("targetAudioReadaheadMs", targetAudioReadaheadMs.toString())
        put("minAudioReadaheadMs", minAudioReadaheadMs.toString())
        put("maxTimeSinceLastRequestMs", maxTimeSinceLastRequestMs.toString())
        put("backoffTimeMs", backoffTimeMs.toString())
        put("transientRetryCount", transientRetryCount.toString())
        put("noProgressRequestCount", noProgressRequestCount.toString())
        httpStatus?.let { put("httpStatus", it.toString()) }
        contentType?.let { put("contentType", it) }
        protectionStatus?.let { put("protectionStatus", it.toString()) }
        protectionMaxRetries?.let { put("protectionMaxRetries", it.toString()) }
        if (receivedSequences.isNotEmpty()) put("receivedSequences", receivedSequences.joinToString(","))
    }
