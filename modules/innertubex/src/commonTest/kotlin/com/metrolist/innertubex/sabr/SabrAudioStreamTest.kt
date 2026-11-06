package com.metrolist.innertubex.sabr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalSabrApi::class)
class SabrAudioStreamTest {
    @Test
    fun videoStreamEmitsOnlySelectedVideoSegments() =
        runBlocking {
            val response =
                umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(0, itag = 248, lastModified = 300)) +
                    mediaSegment(headerId = 1, itag = 140, lastModified = 100, isInit = true, data = byteArrayOf(1)) +
                    mediaSegment(headerId = 2, itag = 248, lastModified = 300, isInit = true, data = byteArrayOf(2, 3)) +
                    mediaSegment(headerId = 3, itag = 140, lastModified = 100, data = byteArrayOf(4)) +
                    mediaSegment(headerId = 4, itag = 248, lastModified = 300, data = byteArrayOf(5, 6, 7)) +
                    umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
            val engine =
                MockEngine {
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }
            val bootstrap =
                bootstrap().copy(
                    selectedVideoFormat = SabrFormatId(248, 300),
                    selectedVideoWidth = 1920,
                    selectedVideoHeight = 1080,
                    selectedVideoContentLengthBytes = 5,
                    selectedVideoMimeType = "video/webm",
                )

            val chunks = SabrVideoStream(HttpClient(engine), bootstrap).bytes().toList()

            assertEquals(2, chunks.size)
            assertContentEquals(byteArrayOf(2, 3), chunks[0])
            assertContentEquals(byteArrayOf(5, 6, 7), chunks[1])
        }

    @Test
    fun emitsCompleteInitializationAndMediaSequence() =
        runBlocking {
            val response = completeResponse(endSegmentNumber = 0)
            val engine =
                MockEngine { request ->
                    assertEquals("0", request.url.parameters["rn"])
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks = SabrAudioStream(HttpClient(engine), bootstrap()).bytes().toList()

            assertEquals(2, chunks.size)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), chunks[0])
            assertContentEquals(byteArrayOf(5, 6, 7), chunks[1])
        }

    @Test
    fun doesNotFollowHttpRedirects() =
        runBlocking {
            val engine =
                MockEngine { request ->
                    assertEquals("preserved", request.headers["X-Caller-Policy"])
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, "https://example.com/steal-sabr"),
                    )
                }

            assertFailsWith<SabrProtocolException> {
                SabrAudioStream(
                    HttpClient(engine) {
                        defaultRequest { header("X-Caller-Policy", "preserved") }
                    },
                    bootstrap(),
                ).bytes().toList()
            }
            assertEquals(1, engine.requestHistory.size)
            Unit
        }

    @Test
    fun rejectsCompletedStreamWithMissingBytes() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = completeResponse(endSegmentNumber = 0),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            assertFailsWith<SabrProtocolException> {
                SabrAudioStream(
                    HttpClient(engine),
                    bootstrap().copy(contentLengthBytes = 8),
                ).bytes().toList()
            }
            Unit
        }

    @Test
    fun rejectsExcessRetainedContexts() =
        runBlocking {
            val response =
                (1..65).fold(byteArrayOf()) { data, type ->
                    data + umpPart(UmpPartType.SABR_CONTEXT_UPDATE, contextUpdate(type))
                }
            val engine =
                MockEngine {
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            assertFailsWith<SabrProtocolException> {
                SabrAudioStream(HttpClient(engine), bootstrap()).bytes().toList()
            }
            Unit
        }

    @Test
    fun emitsInitializationBeforeSabrResponseCompletes() =
        runBlocking {
            val releaseResponseTail = CompletableDeferred<Unit>()
            val firstChunk = CompletableDeferred<ByteArray>()
            val engine =
                MockEngine {
                    val channel = ByteChannel(autoFlush = true)
                    launch {
                        channel.writeFully(initializationResponsePrefix(endSegmentNumber = 0))
                        releaseResponseTail.await()
                        channel.writeFully(mediaResponseSuffix())
                        channel.close()
                    }
                    respond(
                        content = channel,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }
            val chunks = mutableListOf<ByteArray>()
            val collection =
                async {
                    SabrAudioStream(HttpClient(engine), bootstrap()).bytes().collect { chunk ->
                        chunks += chunk
                        if (!firstChunk.isCompleted) firstChunk.complete(chunk)
                    }
                }

            assertContentEquals(byteArrayOf(1, 2, 3, 4), withTimeout(1_000) { firstChunk.await() })
            assertFalse(collection.isCompleted)

            releaseResponseTail.complete(Unit)
            collection.await()
            assertEquals(2, chunks.size)
            assertContentEquals(byteArrayOf(5, 6, 7), chunks.last())
        }

    @Test
    fun seekedHighBitrateVideoEmitsInitializationBeforeOutOfOrderMedia() =
        runBlocking {
            val releaseResponseTail = CompletableDeferred<Unit>()
            val firstMedia = CompletableDeferred<ByteArray>()
            val mediaBeforeTail = CompletableDeferred<List<Int?>>()
            val chunks = mutableListOf<SabrChunk>()
            var requestBody: ByteArray? = null
            val engine =
                MockEngine { request ->
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    val channel = ByteChannel(autoFlush = true)
                    launch {
                        channel.writeFully(
                            umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(7, 62_000, 401, 400)) +
                                mediaSegment(headerId = 1, itag = 248, lastModified = 300, isInit = true, data = byteArrayOf(9)) +
                                mediaResponseSegment(
                                    headerId = 3,
                                    sequenceNumber = 6,
                                    startMs = 60_000,
                                    data = byteArrayOf(3, 4, 5),
                                    itag = 248,
                                    lastModified = 300,
                                ) +
                                mediaResponseSegment(
                                    headerId = 4,
                                    sequenceNumber = 7,
                                    startMs = 61_000,
                                    data = byteArrayOf(6, 7, 8),
                                    itag = 401,
                                    lastModified = 400,
                                ) +
                                mediaResponseSegment(
                                    headerId = 5,
                                    sequenceNumber = 6,
                                    startMs = 60_000,
                                    data = byteArrayOf(9, 10, 11),
                                    itag = 401,
                                    lastModified = 400,
                                ) +
                                mediaSegment(headerId = 2, itag = 401, lastModified = 400, isInit = true, data = byteArrayOf(1, 2)),
                        )
                        releaseResponseTail.await()
                        channel.writeFully(umpPart(UmpPartType.END_OF_TRACK, byteArrayOf()))
                        channel.close()
                    }
                    respond(
                        content = channel,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }
            val bootstrap =
                bootstrap().copy(
                    selectedVideoFormat = SabrFormatId(401, 400, "x"),
                    selectedVideoWidth = 3840,
                    selectedVideoHeight = 2160,
                    selectedVideoContentLengthBytes = 8,
                    selectedVideoMimeType = "video/webm",
                    selectedVideoBitrate = 12_000_000,
                    durationMs = 62_000,
                )
            val collection =
                async {
                    SabrVideoStream(HttpClient(engine), bootstrap, initialPlayerTimeMs = 60_000).chunks().collect { chunk ->
                        chunks += chunk
                        if (!chunk.isInitialization && !firstMedia.isCompleted) firstMedia.complete(chunk.data)
                        if (chunk.sequenceNumber == 7) mediaBeforeTail.complete(chunks.map(SabrChunk::sequenceNumber))
                    }
                }

            assertContentEquals(byteArrayOf(9, 10, 11), withTimeout(1_000) { firstMedia.await() })
            assertEquals(listOf(null, 6, 7), withTimeout(1_000) { mediaBeforeTail.await() })
            assertEquals(60_000, decodePlayerTimeMs(requestBody ?: error("No SABR request")))
            assertFalse(collection.isCompleted)

            releaseResponseTail.complete(Unit)
            collection.await()
        }

    @Test
    fun rejectsEndOfTrackBeforeInitializationMetadataEndSegment() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content = completeResponse(endSegmentNumber = 1),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            assertFailsWith<SabrProtocolException> {
                SabrAudioStream(HttpClient(engine), bootstrap()).bytes().toList()
            }
            Unit
        }

    @Test
    fun retriesTransientSabrHttpFailureBeforePlaybackStarts() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    if (requests == 1) {
                        respond("temporary", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(
                            content = completeResponse(endSegmentNumber = 0),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                        )
                    }
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap(),
                    requestRetryDelay = {},
                ).bytes().toList()

            assertEquals(2, requests)
            assertEquals(2, chunks.size)
        }

    @Test
    fun followupRequestUsesDownloadedBoundaryInsteadOfPlaybackPosition() =
        runBlocking {
            var requestCount = 0
            var followupBody: ByteArray? = null
            val engine =
                MockEngine { request ->
                    requestCount++
                    val response =
                        if (requestCount == 1) {
                            initializationAndSegmentResponse(endSegmentNumber = 1)
                        } else {
                            followupBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                            finalSegmentResponse()
                        }
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                    playbackPositionMs = { 250 },
                ).bytes().toList()

            assertEquals(2, requestCount)
            assertEquals(3, chunks.size)
            assertEquals(1_000, decodePlayerTimeMs(followupBody ?: error("No follow-up request")))
        }

    @Test
    fun followupRetainsBufferedTimeSpanAcrossEmptyResponseAndTimestampRounding() =
        runBlocking {
            val diagnostics = mutableListOf<SabrResponseDiagnostics>()
            val requests = mutableListOf<ByteArray>()
            val engine =
                MockEngine { request ->
                    requests += (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respond(
                        content =
                            when (requests.size) {
                                1 -> {
                                    initializationAndSegmentResponse(endSegmentNumber = 2, durationMs = 3_005)
                                }

                                2 -> {
                                    mediaResponseSegment(
                                        headerId = 3,
                                        sequenceNumber = 1,
                                        startMs = 1_010,
                                        data = byteArrayOf(8, 9, 10),
                                    )
                                }

                                3 -> {
                                    umpPart(UmpPartType.NEXT_REQUEST_POLICY, nextRequestPolicy(0))
                                }

                                else -> {
                                    mediaResponseSegment(
                                        headerId = 4,
                                        sequenceNumber = 2,
                                        startMs = 2_005,
                                        data = byteArrayOf(11, 12, 13),
                                    ) +
                                        umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
                                }
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    HttpClient(engine),
                    bootstrap().copy(durationMs = 3_005, contentLengthBytes = 13),
                    onResponse = diagnostics::add,
                ).chunks().toList()

            assertEquals(listOf(null, 0, 1, 2), chunks.map(SabrChunk::sequenceNumber))
            assertEquals(4, requests.size)
            assertEquals(SabrBufferedRange(SabrFormatId(140, 100, "x"), 0, 2_010, 0, 1), diagnostics[1].bufferedRanges.single())
            assertEquals(diagnostics[1].bufferedRanges, diagnostics[2].bufferedRanges)
            assertEquals(SabrBufferedRange(SabrFormatId(140, 100, "x"), 0, 3_005, 0, 2), diagnostics[3].bufferedRanges.single())
            assertEquals(2_010, decodePlayerTimeMs(requests[3]))
            val range =
                ProtoReader(requests[3]).let { reader ->
                    while (reader.hasRemaining) {
                        val tag = reader.tag()
                        if (tag.field == 3) {
                            val value = reader.bytes()
                            val fields = ProtoReader(value)
                            while (fields.hasRemaining) {
                                val field = fields.tag()
                                if (field.field == 5) return@let fields.varint()
                                fields.skip(field)
                            }
                        } else {
                            reader.skip(tag)
                        }
                    }
                    null
                }
            assertEquals(1L, range)
        }

    @Test
    fun multiSegmentResponsePreservesBufferedRangeDiagnostics() =
        runBlocking {
            val diagnostics = mutableListOf<SabrResponseDiagnostics>()
            val engine =
                MockEngine {
                    respond(
                        content = initializationAndSegmentResponse(endSegmentNumber = 1) + finalSegmentResponse(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                    onResponse = diagnostics::add,
                ).chunks().toList()

            assertEquals(3, chunks.size)
            assertEquals(200, diagnostics.single().httpStatus)
            assertEquals(6, diagnostics.single().selectedMediaBytes)
            assertEquals(2, diagnostics.single().selectedSegmentCount)
            assertEquals(SabrBufferedRange(SabrFormatId(140, 100, "x"), 0, 2_000, 0, 1), diagnostics.single().bufferedRanges.single())
        }

    @Test
    fun duplicateOldAndOutOfOrderNewSegmentsRemainOrdered() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                initializationAndSegmentResponse(endSegmentNumber = 2, durationMs = 3_000)
                            } else {
                                mediaResponseSegment(headerId = 3, sequenceNumber = 0, startMs = 0, data = byteArrayOf(5, 6, 7)) +
                                    mediaResponseSegment(
                                        headerId = 4,
                                        sequenceNumber = 2,
                                        startMs = 2_000,
                                        data = byteArrayOf(11, 12, 13),
                                    ) +
                                    mediaResponseSegment(headerId = 5, sequenceNumber = 1, startMs = 1_000, data = byteArrayOf(8, 9, 10)) +
                                    umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 3_000, contentLengthBytes = 13),
                ).chunks().toList()

            assertEquals(2, requests)
            assertEquals(listOf(null, 0, 1, 2), chunks.map(SabrChunk::sequenceNumber))
        }

    @Test
    fun rejectsUnseenSequenceBelowEmittedRange() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                initializationResponsePrefix(endSegmentNumber = 2, durationMs = 3_000) +
                                    mediaResponseSegment(headerId = 2, sequenceNumber = 1, startMs = 1_000, data = byteArrayOf(8, 9, 10))
                            } else {
                                mediaResponseSegment(headerId = 3, sequenceNumber = 0, startMs = 0, data = byteArrayOf(5, 6, 7)) +
                                    mediaResponseSegment(
                                        headerId = 4,
                                        sequenceNumber = 2,
                                        startMs = 2_000,
                                        data = byteArrayOf(11, 12, 13),
                                    ) +
                                    umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap().copy(durationMs = 3_000, contentLengthBytes = null),
                    ).bytes().toList()
                }

            assertEquals("SABR segment sequence gap: expected 3, received 0", error.message)
            assertEquals(2, requests)
        }

    @Test
    fun finalSegmentCompletesWithoutEndOfTrackPart() =
        runBlocking {
            var requestCount = 0
            val engine =
                MockEngine {
                    requestCount++
                    respond(
                        content =
                            if (requestCount == 1) {
                                initializationAndSegmentResponse(endSegmentNumber = 1, durationMs = 2_100)
                            } else {
                                finalSegmentResponse(includeEndOfTrack = false)
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 2_100, contentLengthBytes = 10),
                ).bytes().toList()

            assertEquals(2, requestCount)
            assertEquals(3, chunks.size)
        }

    @Test
    fun waitsForPlaybackBeforeExceedingServerReadaheadTarget() =
        runBlocking {
            val requestCount = MutableStateFlow(0)
            val playbackPositionMs = MutableStateFlow(0L)
            val firstRequestHandled = CompletableDeferred<Unit>()
            val engine =
                MockEngine {
                    val count = requestCount.value + 1
                    requestCount.value = count
                    val response =
                        if (count == 1) {
                            firstRequestHandled.complete(Unit)
                            initializationAndSegmentResponse(endSegmentNumber = 1, targetAudioReadaheadMs = 500)
                        } else {
                            finalSegmentResponse()
                        }
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                async {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                        playbackPositionMs = { playbackPositionMs.value },
                    ).bytes().toList()
                }

            firstRequestHandled.await()
            delay(100)
            assertEquals(1, requestCount.value)

            playbackPositionMs.value = 600
            assertEquals(3, chunks.await().size)
            assertEquals(2, requestCount.value)
        }

    @Test
    fun initialMediaTimeStartsNearSeekTargetAndAcceptsNonzeroFirstSegment() =
        runBlocking {
            var requestBody: ByteArray? = null
            val engine =
                MockEngine { request ->
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respond(
                        content = seekResponse(sequenceNumber = 6, startMs = 60_000),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 61_000),
                    initialPlayerTimeMs = 60_000,
                ).chunks().toList()

            assertEquals(60_000, decodePlayerTimeMs(requestBody ?: error("No SABR request")))
            assertEquals(listOf(true, false), chunks.map(SabrChunk::isInitialization))
            assertEquals(6, chunks.last().sequenceNumber)
            assertEquals(60_000, chunks.last().startMs)
        }

    @Test
    fun lateSegmentBeforeEstablishedSeekIsIgnored() =
        runBlocking {
            val response =
                umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(7, durationMs = 61_000)) +
                    mediaSegment(headerId = 1, itag = 140, lastModified = 100, isInit = true, data = byteArrayOf(1, 2)) +
                    mediaResponseSegment(
                        headerId = 2,
                        sequenceNumber = 7,
                        startMs = 60_000,
                        data = byteArrayOf(8, 9, 10),
                    ) +
                    mediaResponseSegment(
                        headerId = 3,
                        sequenceNumber = 6,
                        startMs = 59_000,
                        data = byteArrayOf(5, 6, 7),
                    ) +
                    umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
            val engine =
                MockEngine {
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 61_000),
                    initialPlayerTimeMs = 60_500,
                ).chunks().toList()

            assertEquals(listOf(null, 7), chunks.map(SabrChunk::sequenceNumber))
            assertContentEquals(byteArrayOf(8, 9, 10), chunks.last().data)
        }

    @Test
    fun initialSeekWaitsForTargetWhenSequenceZeroArrivesFirst() =
        runBlocking {
            val response =
                umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(6, durationMs = 13_000)) +
                    mediaSegment(headerId = 1, itag = 140, lastModified = 100, isInit = true, data = byteArrayOf(1, 2)) +
                    mediaResponseSegment(
                        headerId = 2,
                        sequenceNumber = 0,
                        startMs = 0,
                        data = byteArrayOf(3, 4, 5),
                    ) +
                    mediaResponseSegment(
                        headerId = 3,
                        sequenceNumber = 6,
                        startMs = 12_000,
                        data = byteArrayOf(8, 9, 10),
                    ) +
                    umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
            val engine =
                MockEngine {
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 13_000),
                    initialPlayerTimeMs = 12_000,
                ).chunks().toList()

            assertEquals(listOf(null, 6), chunks.map(SabrChunk::sequenceNumber))
            assertContentEquals(byteArrayOf(8, 9, 10), chunks.last().data)
        }

    @Test
    fun initialSeekStartsAtContainingSegmentWhenPredecessorArrivesFirst() =
        runBlocking {
            val response =
                umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(7, durationMs = 62_000)) +
                    mediaSegment(headerId = 1, itag = 140, lastModified = 100, isInit = true, data = byteArrayOf(1, 2)) +
                    mediaResponseSegment(
                        headerId = 2,
                        sequenceNumber = 6,
                        startMs = 59_000,
                        data = byteArrayOf(5, 6, 7),
                    ) +
                    mediaResponseSegment(
                        headerId = 3,
                        sequenceNumber = 7,
                        startMs = 60_000,
                        data = byteArrayOf(8, 9, 10),
                    ) +
                    umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())
            val engine =
                MockEngine {
                    respond(
                        content = response,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 62_000),
                    initialPlayerTimeMs = 60_500,
                ).chunks().toList()

            assertEquals(listOf(null, 7), chunks.map(SabrChunk::sequenceNumber))
            assertContentEquals(byteArrayOf(8, 9, 10), chunks.last().data)
        }

    @Test
    fun omittedProtectionAllowanceContinuesWithProgressUntilAccepted() =
        runBlocking {
            val diagnostics = mutableListOf<SabrResponseDiagnostics>()
            var requests = 0
            val engine =
                MockEngine { request ->
                    assertEquals(requests.toString(), request.url.parameters["rn"])
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                initializationAndSegmentResponse(endSegmentNumber = 1) +
                                    umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2))
                            } else {
                                assertEquals(1_000, decodePlayerTimeMs((request.body as OutgoingContent.ByteArrayContent).bytes()))
                                finalSegmentResponse() +
                                    umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 1))
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    HttpClient(engine),
                    bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                    onResponse = diagnostics::add,
                ).chunks().toList()

            assertEquals(listOf(null, 0, 1), chunks.map(SabrChunk::sequenceNumber))
            assertEquals(2, requests)
            assertEquals(2, diagnostics.first().protectionStatus)
            assertEquals(null, diagnostics.first().protectionMaxRetries)
            assertEquals(1, diagnostics.last().protectionStatus)
        }

    @Test
    fun statuslessProgressAfterOmittedPendingCanBeAccepted() =
        runBlocking {
            var requests = 0
            val diagnostics = mutableListOf<SabrResponseDiagnostics>()
            val engine =
                MockEngine {
                    val sequence = requests++
                    respond(
                        content =
                            when (sequence) {
                                0 -> {
                                    initializationAndSegmentResponse(endSegmentNumber = 2, durationMs = 3_000) +
                                        umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2))
                                }

                                1 -> {
                                    mediaResponseSegment(3, 1, 1_000, byteArrayOf(8, 9, 10))
                                }

                                else -> {
                                    mediaResponseSegment(3, 2, 2_000, byteArrayOf(11, 12, 13)) +
                                        umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 1))
                                }
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val chunks =
                SabrAudioStream(
                    HttpClient(engine),
                    bootstrap().copy(durationMs = 3_000, contentLengthBytes = 13),
                    onResponse = diagnostics::add,
                ).chunks().toList()

            assertEquals(listOf(null, 0, 1, 2), chunks.map(SabrChunk::sequenceNumber))
            assertEquals(3, requests)
            assertEquals(listOf(2, null, 1), diagnostics.map(SabrResponseDiagnostics::protectionStatus))
        }

    @Test
    fun videoContinuesThroughOmittedProtectionAllowance() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(1, 2_000, 248, 300)) +
                                    mediaSegment(headerId = 1, itag = 248, lastModified = 300, isInit = true, data = byteArrayOf(1)) +
                                    mediaSegment(headerId = 2, itag = 248, lastModified = 300, data = byteArrayOf(2)) +
                                    umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2))
                            } else {
                                mediaResponseSegment(3, 1, 1_000, byteArrayOf(3), itag = 248, lastModified = 300) +
                                    umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 1))
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }
            val bootstrap =
                bootstrap().copy(
                    durationMs = 2_000,
                    selectedVideoFormat = SabrFormatId(248, 300),
                    selectedVideoWidth = 1920,
                    selectedVideoHeight = 1080,
                    selectedVideoContentLengthBytes = 3,
                )

            assertEquals(
                listOf(null, 0, 1),
                SabrVideoStream(HttpClient(engine), bootstrap).chunks().toList().map(SabrChunk::sequenceNumber),
            )
            assertEquals(2, requests)
        }

    @Test
    fun repeatedOmittedProtectionAllowanceHasFiniteTotalBudget() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    val sequence = requests++
                    respond(
                        content =
                            (
                                if (sequence == 0) {
                                    initializationAndSegmentResponse(endSegmentNumber = 6, durationMs = 7_000)
                                } else {
                                    mediaResponseSegment(3, sequence, sequence * 1_000L, byteArrayOf(8, 9, 10))
                                }
                            ) +
                                umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(HttpClient(engine), bootstrap().copy(durationMs = 7_000, contentLengthBytes = null)).chunks().toList()
                }

            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(6, requests)
        }

    @Test
    fun absentOrUnknownProtectionStatusCannotEvadeOmittedPendingBudget() =
        runBlocking {
            for (status in listOf(null, 0)) {
                var requests = 0
                val diagnostics = mutableListOf<SabrResponseDiagnostics>()
                val engine =
                    MockEngine {
                        val sequence = requests++
                        respond(
                            content =
                                if (sequence == 0) {
                                    initializationAndSegmentResponse(endSegmentNumber = 6, durationMs = 7_000) +
                                        umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2))
                                } else {
                                    mediaResponseSegment(3, sequence, sequence * 1_000L, byteArrayOf(8, 9, 10)) +
                                        (
                                            status?.let {
                                                umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = it))
                                            } ?: byteArrayOf()
                                        )
                                },
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                        )
                    }

                val error =
                    assertFailsWith<SabrProtocolException> {
                        SabrAudioStream(
                            HttpClient(engine),
                            bootstrap().copy(durationMs = 7_000, contentLengthBytes = null),
                            onResponse = diagnostics::add,
                        ).chunks().toList()
                    }

                assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
                assertEquals(6, requests)
                assertEquals(1, diagnostics[5].selectedSegmentCount)
                assertEquals(status, diagnostics[5].protectionStatus)
            }
        }

    @Test
    fun completionWithoutAcceptedProtectionStatusFails() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                initializationAndSegmentResponse(endSegmentNumber = 1) +
                                    umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2))
                            } else {
                                finalSegmentResponse()
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(HttpClient(engine), bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10)).bytes().toList()
                }
            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(2, requests)
        }

    @Test
    fun protectionPendingWithoutMediaIsTypedAsAttestationFailure() =
        runBlocking {
            val diagnostics = mutableListOf<SabrResponseDiagnostics>()
            val engine =
                MockEngine {
                    respond(
                        content = umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap(),
                        onResponse = diagnostics::add,
                    ).bytes().toList()
                }

            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(2, assertNotNull(diagnostics.firstNotNullOfOrNull(SabrResponseDiagnostics::protectionStatus)))
        }

    @Test
    fun protectionPendingPersistsUntilLaterMediaStarvation() =
        runBlocking {
            var requests = 0
            val diagnostics = mutableListOf<SabrResponseDiagnostics>()
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                initializationAndSegmentResponse(endSegmentNumber = 1) +
                                    umpPart(
                                        UmpPartType.STREAM_PROTECTION_STATUS,
                                        streamProtectionStatus(status = 2, maxRetries = 3),
                                    )
                            } else {
                                umpPart(UmpPartType.NEXT_REQUEST_POLICY, nextRequestPolicy(0))
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                        onResponse = diagnostics::add,
                    ).bytes().toList()
                }

            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(2, requests)
            assertEquals(
                1_000L,
                diagnostics
                    .first()
                    .bufferedRanges
                    .single()
                    .durationMs,
            )
        }

    @Test
    fun repeatedPendingStatusHonorsServerRetryBudgetEvenWithMedia() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    val media =
                        if (requests == 1) {
                            initializationAndSegmentResponse(endSegmentNumber = 2)
                        } else {
                            finalSegmentResponse(includeEndOfTrack = false)
                        }
                    respond(
                        content =
                            media +
                                umpPart(
                                    UmpPartType.STREAM_PROTECTION_STATUS,
                                    streamProtectionStatus(status = 2, maxRetries = 1),
                                ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap().copy(durationMs = 3_000, contentLengthBytes = 13),
                    ).bytes().toList()
                }

            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(2, requests)
        }

    @Test
    fun statuslessProgressCannotEvadeExplicitPendingBudget() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            if (requests == 1) {
                                initializationAndSegmentResponse(endSegmentNumber = 2, durationMs = 3_000) +
                                    umpPart(UmpPartType.STREAM_PROTECTION_STATUS, streamProtectionStatus(status = 2, maxRetries = 1))
                            } else {
                                finalSegmentResponse(includeEndOfTrack = false)
                            },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(HttpClient(engine), bootstrap().copy(durationMs = 3_000, contentLengthBytes = 13)).bytes().toList()
                }
            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(2, requests)
        }

    @Test
    fun zeroProtectionRetryBudgetFailsOnFirstPendingResponse() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            initializationAndSegmentResponse(endSegmentNumber = 1) +
                                umpPart(
                                    UmpPartType.STREAM_PROTECTION_STATUS,
                                    streamProtectionStatus(status = 2, maxRetries = 0),
                                ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                    ).bytes().toList()
                }

            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
            assertEquals(1, requests)
        }

    @Test
    fun protectionRequiredIsImmediateEvenWhenResponseContainsMedia() =
        runBlocking {
            val engine =
                MockEngine {
                    respond(
                        content =
                            initializationAndSegmentResponse(endSegmentNumber = 1) +
                                umpPart(
                                    UmpPartType.STREAM_PROTECTION_STATUS,
                                    streamProtectionStatus(status = 3),
                                ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            val error =
                assertFailsWith<SabrProtocolException> {
                    SabrAudioStream(
                        httpClient = HttpClient(engine),
                        bootstrap = bootstrap().copy(durationMs = 2_000, contentLengthBytes = 10),
                    ).bytes().toList()
                }

            assertEquals(SabrFailureKind.ATTESTATION_REQUIRED, error.kind)
        }

    @Test
    fun segmentOvershootStopsBeforeAnotherRequestAndFailsValidation() =
        runBlocking {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    respond(
                        content =
                            initializationAndSegmentResponse(endSegmentNumber = 0, durationMs = 3_000) +
                                finalSegmentResponse(includeEndOfTrack = false),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/vnd.yt-ump"),
                    )
                }

            assertFailsWith<SabrProtocolException> {
                SabrAudioStream(
                    httpClient = HttpClient(engine),
                    bootstrap = bootstrap().copy(durationMs = 3_000, contentLengthBytes = 13),
                ).bytes().toList()
            }

            assertEquals(1, requests)
        }

    private fun completeResponse(endSegmentNumber: Int): ByteArray = initializationResponsePrefix(endSegmentNumber) + mediaResponseSuffix()

    private fun initializationResponsePrefix(
        endSegmentNumber: Int,
        durationMs: Long = 1_000,
    ): ByteArray =
        umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(endSegmentNumber, durationMs = durationMs)) +
            umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 1, isInit = true, sequenceNumber = 0, contentLength = 4)) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(1, 1, 2, 3, 4)) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(1))

    private fun mediaResponseSuffix(): ByteArray =
        umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 2, isInit = false, sequenceNumber = 0, contentLength = 3)) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(2, 5, 6, 7)) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(2)) +
            umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())

    private fun initializationAndSegmentResponse(
        endSegmentNumber: Int,
        targetAudioReadaheadMs: Int? = null,
        durationMs: Long = 2_000,
    ): ByteArray =
        (targetAudioReadaheadMs?.let { umpPart(UmpPartType.NEXT_REQUEST_POLICY, nextRequestPolicy(it)) } ?: byteArrayOf()) +
            umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(endSegmentNumber, durationMs = durationMs)) +
            umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 1, isInit = true, sequenceNumber = 0, contentLength = 4)) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(1, 1, 2, 3, 4)) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(1)) +
            umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 2, isInit = false, sequenceNumber = 0, contentLength = 3)) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(2, 5, 6, 7)) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(2))

    private fun finalSegmentResponse(includeEndOfTrack: Boolean = true): ByteArray =
        mediaResponseSegment(headerId = 3, sequenceNumber = 1, startMs = 1_000, data = byteArrayOf(8, 9, 10)) +
            if (includeEndOfTrack) umpPart(UmpPartType.END_OF_TRACK, byteArrayOf()) else byteArrayOf()

    private fun mediaResponseSegment(
        headerId: Int,
        sequenceNumber: Int,
        startMs: Long,
        data: ByteArray,
        itag: Int = 140,
        lastModified: Long = 100,
    ): ByteArray =
        umpPart(
            UmpPartType.MEDIA_HEADER,
            mediaHeader(
                headerId = headerId,
                isInit = false,
                sequenceNumber = sequenceNumber,
                contentLength = data.size,
                startMs = startMs,
                itag = itag,
                lastModified = lastModified,
            ),
        ) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(headerId.toByte()) + data) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(headerId.toByte()))

    private fun seekResponse(
        sequenceNumber: Int,
        startMs: Long,
    ): ByteArray =
        umpPart(UmpPartType.FORMAT_INITIALIZATION_METADATA, initialization(sequenceNumber, durationMs = startMs + 1_000)) +
            umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 1, isInit = true, sequenceNumber = 0, contentLength = 4)) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(1, 1, 2, 3, 4)) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(1)) +
            umpPart(
                UmpPartType.MEDIA_HEADER,
                mediaHeader(
                    headerId = 2,
                    isInit = false,
                    sequenceNumber = sequenceNumber,
                    contentLength = 3,
                    startMs = startMs,
                ),
            ) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(2, 5, 6, 7)) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(2)) +
            umpPart(UmpPartType.END_OF_TRACK, byteArrayOf())

    private fun streamProtectionStatus(
        status: Int,
        maxRetries: Int? = null,
    ): ByteArray =
        ProtoWriter()
            .apply {
                int32(1, status)
                maxRetries?.let { int32(2, it) }
            }.toByteArray()

    private fun contextUpdate(type: Int): ByteArray =
        ProtoWriter()
            .apply {
                int32(1, type)
                bytes(3, byteArrayOf(type.toByte()))
            }.toByteArray()

    private fun nextRequestPolicy(targetAudioReadaheadMs: Int): ByteArray =
        ProtoWriter()
            .apply {
                int32(1, targetAudioReadaheadMs)
                int32(3, 5_000)
            }.toByteArray()

    private fun initialization(
        endSegmentNumber: Int,
        durationMs: Long = 1_000,
        itag: Int = 140,
        lastModified: Long = 100,
    ): ByteArray =
        ProtoWriter()
            .apply {
                message(2) {
                    int32(1, itag)
                    int64(2, lastModified)
                    string(3, "x")
                }
                int64(3, durationMs)
                int64(4, endSegmentNumber.toLong())
            }.toByteArray()

    private fun mediaHeader(
        headerId: Int,
        isInit: Boolean,
        sequenceNumber: Int,
        contentLength: Int,
        startMs: Long = 0,
        itag: Int = 140,
        lastModified: Long = 100,
    ): ByteArray =
        ProtoWriter()
            .apply {
                int32(1, headerId)
                int32(3, itag)
                int64(4, lastModified)
                string(5, "x")
                if (isInit) bool(8, true)
                int32(9, sequenceNumber)
                if (!isInit) {
                    if (startMs != 0L) int64(11, startMs)
                    int64(12, 1_000)
                }
                int64(14, contentLength.toLong())
            }.toByteArray()

    private fun mediaSegment(
        headerId: Int,
        itag: Int,
        lastModified: Long,
        isInit: Boolean = false,
        data: ByteArray,
    ): ByteArray =
        umpPart(
            UmpPartType.MEDIA_HEADER,
            mediaHeader(
                headerId = headerId,
                isInit = isInit,
                sequenceNumber = 0,
                contentLength = data.size,
                itag = itag,
                lastModified = lastModified,
            ),
        ) +
            umpPart(UmpPartType.MEDIA, byteArrayOf(headerId.toByte()) + data) +
            umpPart(UmpPartType.MEDIA_END, byteArrayOf(headerId.toByte()))

    private fun decodePlayerTimeMs(request: ByteArray): Long {
        val requestReader = ProtoReader(request)
        var clientAbr = byteArrayOf()
        while (requestReader.hasRemaining) {
            val tag = requestReader.tag()
            if (tag.field == 1) {
                clientAbr = requestReader.bytes()
                break
            }
            requestReader.skip(tag)
        }
        val abrReader = ProtoReader(clientAbr)
        while (abrReader.hasRemaining) {
            val tag = abrReader.tag()
            if (tag.field == 28) return abrReader.varint()
            abrReader.skip(tag)
        }
        return 0
    }

    private fun bootstrap() =
        SabrBootstrap(
            videoId = "video-1",
            serverAbrStreamingUrl = "https://example.googlevideo.com/videoplayback?sabr=1",
            videoPlaybackUstreamerConfig = byteArrayOf(1, 2, 3),
            clientName = 3,
            clientVersion = "21.02.35",
            audioFormat = SabrFormatId(140, 100, "x"),
            discardVideoFormat = SabrFormatId(160, 200),
            discardVideoHeight = 144,
            durationMs = 1_000,
            contentLengthBytes = 7,
            mimeType = "audio/mp4",
        )

    private fun umpPart(
        type: Int,
        data: ByteArray,
    ): ByteArray = umpVarint(type) + umpVarint(data.size) + data

    private fun umpVarint(value: Int): ByteArray =
        when {
            value < 128 -> byteArrayOf(value.toByte())
            value < 16_384 -> byteArrayOf(((value and 0x3f) or 0x80).toByte(), (value ushr 6).toByte())
            else -> error("Test payload too large")
        }
}
