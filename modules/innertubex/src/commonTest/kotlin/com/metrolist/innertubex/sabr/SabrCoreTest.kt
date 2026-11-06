package com.metrolist.innertubex.sabr

import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.PlayerResponse
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SabrCoreTest {
    @Test
    fun chunkRejectsOverflowedByteRange() {
        val chunk =
            SabrChunk(
                data = byteArrayOf(1),
                startRange = Long.MAX_VALUE,
                isInitialization = false,
                sequenceNumber = 0,
                startMs = 0,
                durationMs = 1,
            )

        assertFailsWith<IllegalArgumentException> { chunk.endRangeExclusive }
    }

    @OptIn(ExperimentalSabrApi::class)
    @Test
    fun mobileWebSabrUsesMobileRequestOrigin() {
        val audio =
            PlayerResponse.StreamingData.Format(
                itag = 140,
                mimeType = "audio/mp4",
                bitrate = 128_000,
                contentLength = 1_000,
                approxDurationMs = "60000",
                lastModified = "100",
            )
        val response =
            PlayerResponse(
                playabilityStatus = PlayerResponse.PlayabilityStatus("OK"),
                streamingData =
                    PlayerResponse.StreamingData(
                        adaptiveFormats =
                            listOf(
                                audio,
                                PlayerResponse.StreamingData.Format(
                                    itag = 160,
                                    mimeType = "video/mp4",
                                    bitrate = 100_000,
                                    width = 256,
                                    height = 144,
                                    lastModified = "200",
                                ),
                            ),
                        serverAbrStreamingUrl = "https://example.googlevideo.com/videoplayback?sabr=1",
                    ),
                videoDetails =
                    PlayerResponse.VideoDetails(
                        videoId = "video-1",
                        lengthSeconds = "60",
                    ),
                playerConfig =
                    PlayerResponse.PlayerConfig(
                        PlayerResponse.PlayerConfig.MediaCommonConfig(
                            PlayerResponse.PlayerConfig.MediaCommonConfig.MediaUstreamerRequestConfig("AQ=="),
                        ),
                    ),
            )

        val bootstrap = response.toSabrBootstrap(YouTubeClient.MWEB_SABR, audio)

        assertEquals("https://m.youtube.com", bootstrap.requestOrigin)
        assertEquals(bootstrap.requestOrigin, sabrRequestOrigin(YouTubeClient.MWEB_SABR.clientName))
    }

    @Test
    fun videoBootstrapListsOneVariantPerHeightAndRetargetsVideoOnly() {
        fun video(
            itag: Int,
            height: Int,
            bitrate: Int,
            lastModified: String? = "200",
        ) = PlayerResponse.StreamingData.Format(
            itag = itag,
            mimeType = "video/mp4",
            bitrate = bitrate,
            width = height * 16 / 9,
            height = height,
            lastModified = lastModified,
        )
        val selected = video(136, 720, 1_000_000)
        val base = bootstrapPlayerResponse()
        val streaming = checkNotNull(base.streamingData)
        val response =
            base.copy(
                streamingData =
                    streaming.copy(
                        adaptiveFormats =
                            streaming.adaptiveFormats +
                                listOf(
                                    selected,
                                    video(247, 720, 1_500_000),
                                    video(137, 1080, 2_500_000).copy(averageBitrate = 1_800_000),
                                    video(135, 480, 500_000, null),
                                ),
                    ),
            )

        val bootstrap =
            response.toSabrBootstrap(
                clientId = 3,
                clientVersion = "1",
                audioFormat = bootstrapAudioFormat(),
                videoFormat = selected,
            )

        assertEquals(listOf(144 to 160, 720 to 136, 1080 to 137), bootstrap.videoVariants.map { it.height to it.format.itag })
        val retargeted = bootstrap.withVideoVariant(bootstrap.videoVariants.last())
        assertEquals(137, retargeted.selectedVideoFormat?.itag)
        assertEquals(1080, retargeted.selectedVideoHeight)
        assertEquals(1_800_000, retargeted.selectedVideoBitrate)
        assertEquals(bootstrap.audioFormat, retargeted.audioFormat)
        assertTrue(bootstrap.videoPlaybackUstreamerConfig.contentEquals(retargeted.videoPlaybackUstreamerConfig))
        assertFailsWith<IllegalArgumentException> { bootstrap.withVideoVariant(bootstrap.videoVariants.last().copy(height = 2160)) }
        assertTrue(base.toSabrBootstrap(clientId = 3, clientVersion = "1", audioFormat = bootstrapAudioFormat()).videoVariants.isEmpty())
    }

    @Test
    fun sabrUrlRejectsUnexpectedPathsAndUserInfo() {
        assertFailsWith<SabrProtocolException> {
            requireAllowedSabrUrl("https://example.googlevideo.com/not-videoplayback")
        }
        assertFailsWith<SabrProtocolException> {
            requireAllowedSabrUrl("https://user@example.googlevideo.com/videoplayback")
        }
    }

    @Test
    fun bootstrapRejectsOversizedBase64Fields() {
        val audio = bootstrapAudioFormat()

        assertFailsWith<SabrProtocolException> {
            bootstrapPlayerResponse(ustreamerConfig = "A".repeat(100_000)).toSabrBootstrap(
                clientId = 3,
                clientVersion = "1",
                audioFormat = audio,
            )
        }
        assertFailsWith<SabrProtocolException> {
            bootstrapPlayerResponse().toSabrBootstrap(
                clientId = 3,
                clientVersion = "1",
                audioFormat = audio,
                poToken = "A".repeat(100_000),
            )
        }
    }

    @Test
    fun protobufRejectsMalformedVarintsAndFieldNumbers() {
        assertFailsWith<SabrProtocolException> {
            ProtoReader(ByteArray(10) { if (it < 9) 0x80.toByte() else 0x02 }).varint()
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeMediaHeader(byteArrayOf(0x08, 0x81.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x10))
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeMediaHeader(byteArrayOf(0x0a, 0x01, 0x18, 0x01))
        }
        assertFailsWith<SabrProtocolException> { ProtoReader(byteArrayOf(0x01, 0)).tag() }
        assertFailsWith<SabrProtocolException> {
            ProtoReader(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x10)).tag()
        }
        assertFailsWith<SabrProtocolException> {
            ProtoReader(byteArrayOf(0xc0.toByte(), 0xa3.toByte(), 0x09)).tag()
        }
    }

    @Test
    fun omittedProtectionAllowanceDiffersFromExplicitZero() {
        val omitted = SabrProtoCodec.decodeStreamProtectionStatus(byteArrayOf(0x08, 0x02))
        val explicitZero = SabrProtoCodec.decodeStreamProtectionStatus(byteArrayOf(0x08, 0x02, 0x10, 0x00))

        assertEquals(2, omitted.status)
        assertEquals(-1, omitted.maxRetries)
        assertEquals(0, explicitZero.maxRetries)
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeStreamProtectionStatus(ProtoWriter().apply { int32(2, -1) }.toByteArray())
        }
    }

    @Test
    fun protobufDecodersRejectWrongWireTypesAndIntOverflows() {
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeFormatInitialization(byteArrayOf(0x10, 0x00))
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeError(byteArrayOf(0x10, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x10))
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeContextUpdate(byteArrayOf(0x18, 0x00))
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeStreamProtectionStatus(byteArrayOf(0x10, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x10))
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeContextSendingPolicy(byteArrayOf(0x08, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x10))
        }
        assertFailsWith<SabrProtocolException> {
            SabrProtoCodec.decodeFormatInitialization(
                byteArrayOf(
                    0x18,
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0xff.toByte(),
                    0x01,
                ),
            )
        }
    }

    @Test
    fun umpReaderHandlesEveryPossibleChunkBoundary() {
        val encoded =
            umpPart(20, byteArrayOf(1, 2)) +
                umpPart(21, ByteArray(20_000) { (it and 0xff).toByte() }) +
                umpPart(150, byteArrayOf(3, 4, 5))
        val expected = listOf(20 to 2, 21 to 20_000, 150 to 3)

        for (splitSize in 1..127) {
            val reader = UmpReader()
            val actual = mutableListOf<Pair<Int, Int>>()
            encoded.asList().chunked(splitSize).forEach { chunk ->
                actual += reader.feed(chunk.toByteArray()).map { it.type to it.data.size }
            }
            reader.finish()
            assertEquals(expected, actual, "split size $splitSize")
        }
    }

    @Test
    fun processorJoinsMediaPartsByHeaderId() {
        val response =
            umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 9, contentLength = 5, isInit = true)) +
                umpPart(UmpPartType.MEDIA, byteArrayOf(9, 1, 2)) +
                umpPart(UmpPartType.MEDIA, byteArrayOf(9, 3, 4, 5)) +
                umpPart(UmpPartType.MEDIA_END, byteArrayOf(9))
        val processor = SabrUmpProcessor()
        val events = response.asList().chunked(3).flatMap { processor.feed(it.toByteArray()) }
        processor.finish()

        val segment = events.filterIsInstance<SabrEvent.Segment>().single().segment
        assertEquals(SabrFormatId(140, 100, "x"), segment.header.formatId)
        assertTrue(segment.header.isInitSegment)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), segment.data)
    }

    @Test
    fun processorReadsUnsignedByteMediaHeaderIds() {
        // Media payload IDs are bytes, unlike the UMP framing varints.
        val response =
            umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(headerId = 128, contentLength = 3)) +
                umpPart(UmpPartType.MEDIA, byteArrayOf(128.toByte(), 1, 2)) +
                umpPart(UmpPartType.MEDIA, byteArrayOf(128.toByte(), 3)) +
                umpPart(UmpPartType.MEDIA_END, byteArrayOf(128.toByte()))
        val processor = SabrUmpProcessor()
        val events = processor.feed(response)
        processor.finish()

        assertEquals(
            128,
            events
                .filterIsInstance<SabrEvent.Segment>()
                .single()
                .segment.header.headerId,
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            events
                .filterIsInstance<SabrEvent.Segment>()
                .single()
                .segment.data,
        )
    }

    @Test
    fun processorCapsTotalResponseBytes() {
        val response = umpPart(20, ByteArray(64))
        val processor = SabrUmpProcessor(maxResponseBytes = response.size.toLong() - 1)

        assertFailsWith<SabrProtocolException> { processor.feed(response) }
    }

    @Test
    fun processorRejectsContentLengthMismatch() {
        val processor = SabrUmpProcessor()
        processor.feed(umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(contentLength = 4)))
        processor.feed(umpPart(UmpPartType.MEDIA, byteArrayOf(1, 1, 2, 3)))

        assertFailsWith<SabrProtocolException> {
            processor.feed(umpPart(UmpPartType.MEDIA_END, byteArrayOf(1)))
        }
    }

    @Test
    fun processorRejectsCompressedSelectedMedia() {
        val processor = SabrUmpProcessor(shouldCollectMedia = { it.formatId.itag == 140 })
        assertFailsWith<SabrProtocolException> {
            processor.feed(umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(compression = 1)))
        }
    }

    @Test
    fun processorDoesNotCollectDiscardedVideo() {
        val processor = SabrUmpProcessor(shouldCollectMedia = { it.formatId.itag == 140 })
        processor.feed(umpPart(UmpPartType.MEDIA_HEADER, mediaHeader(itag = 160, contentLength = 4)))
        processor.feed(umpPart(UmpPartType.MEDIA, byteArrayOf(1, 1, 2, 3, 4)))
        val events = processor.feed(umpPart(UmpPartType.MEDIA_END, byteArrayOf(1)))
        processor.finish()

        assertTrue(events.isEmpty())
    }

    @Test
    fun formatInitializationPreservesCompletenessMetadata() {
        val payload =
            ProtoWriter()
                .apply {
                    message(2) {
                        int32(1, 140)
                        int64(2, 100)
                        string(3, "x")
                    }
                    int64(3, 60_000)
                    int64(4, 12)
                    int64(9, 2_880_000)
                    int64(10, 48_000)
                }.toByteArray()

        val initialization = SabrProtoCodec.decodeFormatInitialization(payload)
        assertEquals(SabrFormatId(140, 100, "x"), initialization?.formatId)
        assertEquals(12, initialization?.endSegmentNumber)
        assertEquals(60_000, initialization?.durationMs)
    }

    @Test
    fun processorDecodesPackedContextSendingPolicy() {
        val policy =
            ProtoWriter()
                .apply {
                    packedInt32(1, listOf(4, 8))
                    packedInt32(2, listOf(4))
                    packedInt32(3, listOf(9))
                }.toByteArray()
        val event =
            SabrUmpProcessor()
                .feed(umpPart(UmpPartType.SABR_CONTEXT_SENDING_POLICY, policy))
                .filterIsInstance<SabrEvent.ContextSendingPolicy>()
                .single()

        assertEquals(setOf(4, 8), event.start)
        assertEquals(setOf(4), event.stop)
        assertEquals(setOf(9), event.discard)
    }

    @Test
    fun processorRejectsExcessContextPolicyEntriesBeforeRetention() {
        val policy =
            ProtoWriter()
                .apply { packedInt32(1, List(257) { it }) }
                .toByteArray()

        assertFailsWith<SabrProtocolException> {
            SabrUmpProcessor().feed(umpPart(UmpPartType.SABR_CONTEXT_SENDING_POLICY, policy))
        }
    }

    @Test
    fun nextRequestPolicyPreservesPacingFields() {
        val policy =
            ProtoWriter()
                .apply {
                    int32(1, 600_000)
                    int32(3, 60_000)
                    int32(4, 2_000)
                    int32(5, 10_000)
                    bytes(7, byteArrayOf(1, 2, 3))
                }.toByteArray()

        val event = SabrProtoCodec.decodeNextRequestPolicy(policy)

        assertEquals(600_000, event.targetAudioReadaheadMs)
        assertEquals(60_000, event.maxTimeSinceLastRequestMs)
        assertEquals(2_000, event.backoffTimeMs)
        assertEquals(10_000, event.minAudioReadaheadMs)
        assertContentEquals(byteArrayOf(1, 2, 3), event.playbackCookie)
    }

    @Test
    fun formatIdentityUsesLastModifiedAndXtags() {
        val selected = SabrFormatId(140, 100, "audio_track=main")

        assertTrue(selected.matches(SabrFormatId(140, 100, "audio_track=main")))
        assertTrue(!SabrFormatId(140, 99, "audio_track=main").matches(selected))
        assertTrue(!SabrFormatId(140, 100, "audio_track=dubbed").matches(selected))
    }

    @Test
    fun firstAudioOnlyRequestMatchesReferenceEncoder() {
        val bootstrap =
            SabrBootstrap(
                videoId = "video-1",
                serverAbrStreamingUrl = "https://example.googlevideo.com/videoplayback?sabr=1",
                videoPlaybackUstreamerConfig = byteArrayOf(1, 2, 3),
                clientName = 3,
                clientVersion = "21.02.35",
                audioFormat = SabrFormatId(140, 100),
                discardVideoFormat = SabrFormatId(160, 200),
                discardVideoHeight = 144,
                durationMs = 60_000,
                contentLengthBytes = null,
                mimeType = "audio/mp4",
            )
        val request =
            SabrProtoCodec.encodeRequest(
                bootstrap = bootstrap,
                playerTimeMs = 0,
                initialized = false,
                bufferedRanges = emptyList(),
                playbackCookie = null,
                contexts = emptyList(),
            )
        assertEquals(
            "0a10a80190019002019d020000803fc00201120608a00110c8011a250a0608a00110c801" +
                "18ffffffff0720ffffffff0728ffffffff07320910ffffffff0718e8072a030102038201" +
                "05088c0110648a010608a00110c8019a01120a0e8001038a010832312e30322e33353200",
            request.toHex(),
        )
        assertContentEquals(byteArrayOf(1, 2, 3), protobufBytesField(request, 5))
        assertNull(protobufOptionalBytesField(requireNotNull(protobufOptionalBytesField(request, 19)), 3))
    }

    @Test
    fun videoRequestSelectsVideoAndDiscardsAudio() {
        val bootstrap =
            SabrBootstrap(
                videoId = "video-1",
                serverAbrStreamingUrl = "https://example.googlevideo.com/videoplayback?sabr=1",
                videoPlaybackUstreamerConfig = byteArrayOf(1, 2, 3),
                clientName = 3,
                clientVersion = "21.02.35",
                audioFormat = SabrFormatId(140, 100),
                discardVideoFormat = SabrFormatId(160, 200),
                discardVideoHeight = 144,
                selectedVideoFormat = SabrFormatId(248, 300),
                selectedVideoWidth = 1920,
                selectedVideoHeight = 1080,
                durationMs = 60_000,
                contentLengthBytes = null,
                mimeType = "audio/mp4",
            )

        val request =
            SabrProtoCodec.encodeRequest(
                bootstrap = bootstrap,
                playerTimeMs = 0,
                initialized = false,
                bufferedRanges = emptyList(),
                playbackCookie = null,
                contexts = emptyList(),
                mediaType = SabrMediaType.VIDEO,
            )

        val discardRange = protobufRepeatedBytesFields(request, 3).single()
        assertEquals(140, protobufIntField(protobufBytesField(discardRange, 1), 1))
        assertEquals(Int.MAX_VALUE, protobufIntField(discardRange, 4))
        assertEquals(140, protobufIntField(protobufRepeatedBytesFields(request, 2).single(), 1))
        assertEquals(248, protobufIntField(protobufBytesField(request, 17), 1))
        assertEquals(1080, protobufIntField(protobufBytesField(request, 1), 21))
        assertEquals(2, protobufIntField(protobufBytesField(request, 1), 40))
    }

    @Test
    fun followupRequestWritesPlayerTimeOnlyInsideClientAbrState() {
        val bootstrap =
            SabrBootstrap(
                videoId = "video-1",
                serverAbrStreamingUrl = "https://example.googlevideo.com/videoplayback?sabr=1",
                videoPlaybackUstreamerConfig = byteArrayOf(1),
                clientName = 3,
                clientVersion = "21.02.35",
                audioFormat = SabrFormatId(140, 100),
                discardVideoFormat = SabrFormatId(160, 200),
                discardVideoHeight = 144,
                durationMs = 60_000,
                contentLengthBytes = null,
                mimeType = "audio/mp4",
                poToken = byteArrayOf(7, 8, 9),
            )
        val request =
            SabrProtoCodec.encodeRequest(
                bootstrap = bootstrap,
                playerTimeMs = 12_345,
                initialized = true,
                bufferedRanges = emptyList(),
                playbackCookie = null,
                contexts = emptyList(),
            )
        val topLevel = ProtoReader(request)
        var clientAbrState: ByteArray? = null
        var hasOnesieStartTime = false
        while (topLevel.hasRemaining) {
            val tag = topLevel.tag()
            when (tag.field) {
                1 -> {
                    clientAbrState = topLevel.bytes()
                }

                4 -> {
                    hasOnesieStartTime = true
                    topLevel.varint()
                }

                else -> {
                    topLevel.skip(tag)
                }
            }
        }
        val clientAbr = ProtoReader(requireNotNull(clientAbrState))
        var playerTimeMs: Long? = null
        while (clientAbr.hasRemaining) {
            val tag = clientAbr.tag()
            if (tag.field == 28) playerTimeMs = clientAbr.varint() else clientAbr.skip(tag)
        }

        assertEquals(12_345, playerTimeMs)
        assertEquals(false, hasOnesieStartTime)
        val streamerContext = requireNotNull(protobufOptionalBytesField(request, 19))
        assertContentEquals(byteArrayOf(7, 8, 9), protobufBytesField(streamerContext, 2))
    }

    private fun mediaHeader(
        headerId: Int = 1,
        itag: Int = 140,
        contentLength: Int = 4,
        isInit: Boolean = false,
        compression: Int = 0,
    ): ByteArray =
        ProtoWriter()
            .apply {
                int32(1, headerId)
                int32(3, itag)
                int64(4, 100)
                string(5, "x")
                if (compression != 0) int32(7, compression)
                if (isInit) bool(8, true)
                int64(14, contentLength.toLong())
            }.toByteArray()

    private fun bootstrapAudioFormat() =
        PlayerResponse.StreamingData.Format(
            itag = 140,
            mimeType = "audio/mp4",
            bitrate = 128_000,
            contentLength = 1_000,
            approxDurationMs = "60000",
            lastModified = "100",
        )

    private fun bootstrapPlayerResponse(ustreamerConfig: String = "AQ==") =
        PlayerResponse(
            playabilityStatus = PlayerResponse.PlayabilityStatus("OK"),
            streamingData =
                PlayerResponse.StreamingData(
                    adaptiveFormats =
                        listOf(
                            bootstrapAudioFormat(),
                            PlayerResponse.StreamingData.Format(
                                itag = 160,
                                mimeType = "video/mp4",
                                bitrate = 100_000,
                                width = 256,
                                height = 144,
                                lastModified = "200",
                            ),
                        ),
                    serverAbrStreamingUrl = "https://example.googlevideo.com/videoplayback?sabr=1",
                ),
            videoDetails = PlayerResponse.VideoDetails(videoId = "video-1", lengthSeconds = "60"),
            playerConfig =
                PlayerResponse.PlayerConfig(
                    PlayerResponse.PlayerConfig.MediaCommonConfig(
                        PlayerResponse.PlayerConfig.MediaCommonConfig.MediaUstreamerRequestConfig(ustreamerConfig),
                    ),
                ),
        )

    private fun protobufBytesField(
        data: ByteArray,
        field: Int,
    ): ByteArray = requireNotNull(protobufOptionalBytesField(data, field)) { "Missing field $field" }

    private fun protobufOptionalBytesField(
        data: ByteArray,
        field: Int,
    ): ByteArray? {
        val reader = ProtoReader(data)
        while (reader.hasRemaining) {
            val tag = reader.tag()
            if (tag.field == field) return reader.bytes()
            reader.skip(tag)
        }
        return null
    }

    private fun protobufRepeatedBytesFields(
        data: ByteArray,
        field: Int,
    ): List<ByteArray> {
        val values = mutableListOf<ByteArray>()
        val reader = ProtoReader(data)
        while (reader.hasRemaining) {
            val tag = reader.tag()
            if (tag.field == field) values += reader.bytes() else reader.skip(tag)
        }
        return values
    }

    private fun protobufIntField(
        data: ByteArray,
        field: Int,
    ): Int {
        val reader = ProtoReader(data)
        while (reader.hasRemaining) {
            val tag = reader.tag()
            if (tag.field == field) return reader.varint().toInt()
            reader.skip(tag)
        }
        error("Missing field $field")
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun umpPart(
        type: Int,
        data: ByteArray,
    ): ByteArray = umpVarint(type) + umpVarint(data.size) + data

    private fun umpVarint(value: Int): ByteArray =
        when {
            value < 128 -> {
                byteArrayOf(value.toByte())
            }

            value < 16_384 -> {
                byteArrayOf(((value and 0x3f) or 0x80).toByte(), (value ushr 6).toByte())
            }

            value < 2_097_152 -> {
                byteArrayOf(
                    ((value and 0x1f) or 0xc0).toByte(),
                    ((value ushr 5) and 0xff).toByte(),
                    (value ushr 13).toByte(),
                )
            }

            value < 268_435_456 -> {
                byteArrayOf(
                    ((value and 0x0f) or 0xe0).toByte(),
                    ((value ushr 4) and 0xff).toByte(),
                    ((value ushr 12) and 0xff).toByte(),
                    (value ushr 20).toByte(),
                )
            }

            else -> {
                byteArrayOf(
                    0xf0.toByte(),
                    value.toByte(),
                    (value ushr 8).toByte(),
                    (value ushr 16).toByte(),
                    (value ushr 24).toByte(),
                )
            }
        }
}
