package com.metrolist.innertubex.sabr

internal data class UmpPart(
    val type: Int,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is UmpPart && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * type + data.contentHashCode()
}

/** Incremental reader for YouTube's UMP framing. */
internal class UmpReader(
    private val maxPartSize: Int = DEFAULT_MAX_PART_SIZE,
) {
    private var buffer = ByteArray(0)
    private var start = 0
    private var end = 0

    fun feed(chunk: ByteArray): List<UmpPart> = feed(chunk, offset = 0, length = chunk.size)

    internal fun feed(
        chunk: ByteArray,
        offset: Int,
        length: Int,
    ): List<UmpPart> {
        val parts = mutableListOf<UmpPart>()
        feed(chunk, offset, length) { type, data, from, to -> parts += UmpPart(type, data.copyOfRange(from, to)) }
        return parts
    }

    /**
     * Calls [onPart] with each complete part's payload as `data[from, to)`, without copying it.
     * The range is only valid during the callback.
     */
    internal fun feed(
        chunk: ByteArray,
        offset: Int,
        length: Int,
        onPart: (type: Int, data: ByteArray, from: Int, to: Int) -> Unit,
    ) {
        require(offset >= 0 && length >= 0 && offset <= chunk.size - length) { "Invalid UMP input range" }
        if (pendingSize.toLong() + length > MAX_PENDING_BYTES) {
            throw SabrProtocolException("Incomplete UMP part exceeded the pending byte limit")
        }
        if (length > 0) {
            ensureCapacity(length)
            chunk.copyInto(buffer, destinationOffset = end, startIndex = offset, endIndex = offset + length)
            end += length
        }
        if (pendingSize == 0) return

        var parseOffset = start
        while (parseOffset < end) {
            val type = readUmpVarint(buffer, parseOffset, end) ?: break
            val size = readUmpVarint(buffer, type.nextOffset, end) ?: break
            if (size.value > maxPartSize) {
                throw SabrProtocolException("UMP part is too large: ${size.value} bytes")
            }
            val partEnd = size.nextOffset.toLong() + size.value
            if (partEnd > end) break
            onPart(type.value, buffer, size.nextOffset, partEnd.toInt())
            parseOffset = partEnd.toInt()
        }

        start = parseOffset
        if (start == end) {
            start = 0
            end = 0
        }
    }

    fun finish() {
        if (pendingSize > 0) throw SabrProtocolException("Truncated UMP response ($pendingSize bytes remain)")
    }

    private val pendingSize: Int
        get() = end - start

    private fun ensureCapacity(additionalBytes: Int) {
        val required = pendingSize.toLong() + additionalBytes
        if (required > MAX_PENDING_BYTES) {
            throw SabrProtocolException("Incomplete UMP part exceeded the pending byte limit")
        }
        if (buffer.size - end >= additionalBytes) return
        if (start > 0 && buffer.size.toLong() >= required) {
            buffer.copyInto(buffer, destinationOffset = 0, startIndex = start, endIndex = end)
            end = pendingSize
            start = 0
            return
        }

        val doubledCapacity = buffer.size.coerceAtLeast(DEFAULT_BUFFER_SIZE).toLong() * 2
        val newCapacity = maxOf(required, doubledCapacity).coerceAtMost(MAX_PENDING_BYTES).toInt()
        val expanded = ByteArray(newCapacity)
        buffer.copyInto(expanded, destinationOffset = 0, startIndex = start, endIndex = end)
        end = pendingSize
        start = 0
        buffer = expanded
    }

    private fun readUmpVarint(
        data: ByteArray,
        offset: Int,
        limit: Int,
    ): UmpVarint? {
        if (offset >= limit) return null
        val first = data[offset].toInt() and 0xff
        val byteLength =
            when {
                first < 0x80 -> 1
                first < 0xc0 -> 2
                first < 0xe0 -> 3
                first < 0xf0 -> 4
                else -> 5
            }
        if (limit - offset < byteLength) return null

        fun byte(index: Int): Int = data[offset + index].toInt() and 0xff

        val value =
            when (byteLength) {
                1 -> first
                2 -> (first and 0x3f) + 64 * byte(1)
                3 -> (first and 0x1f) + 32 * (byte(1) + 256 * byte(2))
                4 -> (first and 0x0f) + 16 * (byte(1) + 256 * (byte(2) + 256 * byte(3)))
                else -> byte(1) + 256 * (byte(2) + 256 * (byte(3) + 256 * byte(4)))
            }
        if (value < 0) throw SabrProtocolException("UMP varint exceeds Int.MAX_VALUE")
        return UmpVarint(value, offset + byteLength)
    }

    private data class UmpVarint(
        val value: Int,
        val nextOffset: Int,
    )

    companion object {
        const val DEFAULT_MAX_PART_SIZE: Int = 8 * 1024 * 1024
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
        private const val MAX_PENDING_BYTES: Long = 16L * 1024 * 1024
    }
}

internal object UmpPartType {
    const val MEDIA_HEADER = 20
    const val MEDIA = 21
    const val MEDIA_END = 22
    const val NEXT_REQUEST_POLICY = 35
    const val FORMAT_INITIALIZATION_METADATA = 42
    const val SABR_REDIRECT = 43
    const val SABR_ERROR = 44
    const val RELOAD_PLAYER_RESPONSE = 46
    const val SABR_CONTEXT_UPDATE = 57
    const val STREAM_PROTECTION_STATUS = 58
    const val SABR_CONTEXT_SENDING_POLICY = 59
    const val END_OF_TRACK = 62
}
