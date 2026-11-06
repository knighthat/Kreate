package com.metrolist.innertubex.sabr

/** Converts framed UMP parts into complete media segments and session-control events. */
internal class SabrUmpProcessor(
    maxPartSize: Int = UmpReader.DEFAULT_MAX_PART_SIZE,
    private val shouldCollectMedia: (SabrMediaHeader) -> Boolean = { true },
    private val maxCollectedSegmentSize: Int = DEFAULT_MAX_COLLECTED_SEGMENT_SIZE,
    private val maxPendingMediaBytes: Int = DEFAULT_MAX_PENDING_MEDIA_BYTES,
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
) {
    private val reader = UmpReader(maxPartSize)
    private val pendingSegments = mutableMapOf<Int, PendingSegment>()
    private var pendingMediaBytes = 0L
    private var responseBytes = 0L

    fun feed(chunk: ByteArray): List<SabrEvent> = feed(chunk, chunk.size)

    internal fun feed(
        chunk: ByteArray,
        length: Int,
    ): List<SabrEvent> {
        accountResponseBytes(length)
        val events = mutableListOf<SabrEvent>()
        reader.feed(chunk, offset = 0, length = length) { type, data, from, to ->
            // MEDIA payloads go straight from the reader buffer into the segment; other parts are small.
            if (type == UmpPartType.MEDIA) {
                appendMedia(data, from, to)
            } else {
                events += process(UmpPart(type, data.copyOfRange(from, to)))
            }
        }
        return events
    }

    private fun accountResponseBytes(bytes: Int) {
        require(bytes >= 0) { "Response byte count must not be negative" }
        if (responseBytes > maxResponseBytes - bytes) {
            throw SabrProtocolException("SABR response exceeded $maxResponseBytes bytes")
        }
        responseBytes += bytes
    }

    fun finish() {
        reader.finish()
        if (pendingSegments.isNotEmpty()) {
            throw SabrProtocolException(
                "SABR response ended with incomplete media headers: ${pendingSegments.keys.sorted().joinToString()}",
            )
        }
    }

    private fun process(part: UmpPart): List<SabrEvent> =
        when (part.type) {
            UmpPartType.MEDIA_HEADER -> {
                val header = SabrProtoCodec.decodeMediaHeader(part.data)
                if (pendingSegments.containsKey(header.headerId)) {
                    throw SabrProtocolException("Duplicate SABR media header ID ${header.headerId}")
                }
                val collect = shouldCollectMedia(header)
                if (collect && header.compressionAlgorithm != COMPRESSION_NONE) {
                    throw SabrProtocolException(
                        "Unsupported compression ${header.compressionAlgorithm} for SABR media segment",
                    )
                }
                if (collect && header.contentLength > maxCollectedSegmentSize) {
                    throw SabrProtocolException("SABR segment is too large: ${header.contentLength} bytes")
                }
                pendingSegments[header.headerId] = PendingSegment(header, collect)
                listOf(SabrEvent.MediaHeader(header))
            }

            UmpPartType.MEDIA -> {
                appendMedia(part.data, 0, part.data.size)
                emptyList()
            }

            UmpPartType.MEDIA_END -> {
                listOfNotNull(finalizeMedia(part.data))
            }

            UmpPartType.FORMAT_INITIALIZATION_METADATA -> {
                listOf(SabrEvent.FormatInitialized(SabrProtoCodec.decodeFormatInitialization(part.data)))
            }

            UmpPartType.NEXT_REQUEST_POLICY -> {
                listOf(SabrProtoCodec.decodeNextRequestPolicy(part.data))
            }

            UmpPartType.SABR_REDIRECT -> {
                SabrProtoCodec.decodeRedirect(part.data)?.let { listOf(SabrEvent.Redirect(it)) }.orEmpty()
            }

            UmpPartType.SABR_ERROR -> {
                listOf(SabrProtoCodec.decodeError(part.data))
            }

            UmpPartType.RELOAD_PLAYER_RESPONSE -> {
                listOf(SabrEvent.ReloadPlayerResponse)
            }

            UmpPartType.SABR_CONTEXT_UPDATE -> {
                SabrProtoCodec.decodeContextUpdate(part.data)?.let { listOf(SabrEvent.ContextUpdate(it)) }.orEmpty()
            }

            UmpPartType.STREAM_PROTECTION_STATUS -> {
                listOf(SabrProtoCodec.decodeStreamProtectionStatus(part.data))
            }

            UmpPartType.SABR_CONTEXT_SENDING_POLICY -> {
                listOf(SabrProtoCodec.decodeContextSendingPolicy(part.data))
            }

            UmpPartType.END_OF_TRACK -> {
                listOf(SabrEvent.EndOfTrack)
            }

            else -> {
                emptyList()
            }
        }

    private fun appendMedia(
        data: ByteArray,
        from: Int,
        to: Int,
    ) {
        if (from >= to) throw SabrProtocolException("Empty UMP MEDIA part")
        val headerId = data[from].toInt() and 0xff
        val segment =
            pendingSegments[headerId]
                ?: throw SabrProtocolException("UMP MEDIA refers to unknown header ID $headerId")
        val payloadSize = to - from - 1
        val destinationOffset = segment.byteCount.toInt()
        segment.byteCount += payloadSize
        if (segment.header.contentLength > 0 && segment.byteCount > segment.header.contentLength) {
            throw SabrProtocolException("SABR segment ${segment.header.sequenceNumber} exceeded its content length")
        }
        if (!segment.collect || payloadSize == 0) return
        if (segment.byteCount > maxCollectedSegmentSize) {
            throw SabrProtocolException("SABR segment is too large: ${segment.byteCount} bytes")
        }
        if (segment.header.contentLength > 0) {
            var bytes = segment.bytes
            if (bytes == null) {
                val contentLength = segment.header.contentLength.toInt()
                reservePendingBytes(segment, contentLength)
                bytes = ByteArray(contentLength)
                segment.bytes = bytes
            }
            data.copyInto(bytes, destinationOffset = destinationOffset, startIndex = from + 1, endIndex = to)
        } else {
            reservePendingBytes(segment, payloadSize)
            segment.chunks += data.copyOfRange(from + 1, to)
        }
    }

    private fun reservePendingBytes(
        segment: PendingSegment,
        bytes: Int,
    ) {
        if (pendingMediaBytes + bytes > maxPendingMediaBytes) {
            throw SabrProtocolException("SABR pending media exceeded $maxPendingMediaBytes bytes")
        }
        pendingMediaBytes += bytes
        segment.reservedBytes += bytes
    }

    private fun finalizeMedia(data: ByteArray): SabrEvent.Segment? {
        if (data.isEmpty()) throw SabrProtocolException("Empty UMP MEDIA_END part")
        val headerId = data[0].toInt() and 0xff
        val pending =
            pendingSegments.remove(headerId)
                ?: throw SabrProtocolException("UMP MEDIA_END refers to unknown header ID $headerId")
        if (pending.header.contentLength > 0 && pending.byteCount != pending.header.contentLength) {
            throw SabrProtocolException(
                "SABR segment ${pending.header.sequenceNumber} has ${pending.byteCount} bytes, " +
                    "expected ${pending.header.contentLength}",
            )
        }
        if (!pending.collect) return null
        pendingMediaBytes -= pending.reservedBytes
        val bytes =
            pending.bytes ?: ByteArray(pending.byteCount.toInt()).also { combined ->
                var offset = 0
                pending.chunks.forEach { chunk ->
                    chunk.copyInto(combined, destinationOffset = offset)
                    offset += chunk.size
                }
            }
        return SabrEvent.Segment(SabrSegment(pending.header, bytes))
    }

    private data class PendingSegment(
        val header: SabrMediaHeader,
        val collect: Boolean,
        val chunks: MutableList<ByteArray> = mutableListOf(),
        var bytes: ByteArray? = null,
        var byteCount: Long = 0,
        var reservedBytes: Long = 0,
    )

    private companion object {
        const val COMPRESSION_NONE = 0
        const val DEFAULT_MAX_COLLECTED_SEGMENT_SIZE = 16 * 1024 * 1024
        const val DEFAULT_MAX_PENDING_MEDIA_BYTES = 32 * 1024 * 1024
        const val DEFAULT_MAX_RESPONSE_BYTES = 96L * 1024 * 1024
    }
}
