package com.metrolist.innertubex.sabr

private const val MAX_PROTOBUF_FIELD_NUMBER = 536_870_911
private const val FIRST_RESERVED_FIELD_NUMBER = 19_000
private const val LAST_RESERVED_FIELD_NUMBER = 19_999

private fun isValidProtobufFieldNumber(field: Long): Boolean =
    field in 1..MAX_PROTOBUF_FIELD_NUMBER.toLong() && field !in FIRST_RESERVED_FIELD_NUMBER.toLong()..LAST_RESERVED_FIELD_NUMBER.toLong()

internal class ProtoWriter(
    initialCapacity: Int = 256,
) {
    private var buffer = ByteArray(initialCapacity.coerceAtLeast(1))
    private var size = 0

    fun int32(
        field: Int,
        value: Int,
    ) {
        tag(field, WIRE_VARINT)
        varint(value.toLong())
    }

    fun int64(
        field: Int,
        value: Long,
    ) {
        tag(field, WIRE_VARINT)
        varint(value)
    }

    fun bool(
        field: Int,
        value: Boolean,
    ) = int32(field, if (value) 1 else 0)

    fun float(
        field: Int,
        value: Float,
    ) {
        tag(field, WIRE_FIXED32)
        fixed32(value.toRawBits())
    }

    fun string(
        field: Int,
        value: String,
    ) = bytes(field, value.encodeToByteArray())

    fun bytes(
        field: Int,
        value: ByteArray,
    ) {
        tag(field, WIRE_LENGTH_DELIMITED)
        varint(value.size.toLong())
        append(value)
    }

    fun packedInt32(
        field: Int,
        values: Collection<Int>,
    ) {
        val packed = ProtoWriter()
        values.forEach { packed.varint(it.toLong()) }
        bytes(field, packed.toByteArray())
    }

    fun message(
        field: Int,
        block: ProtoWriter.() -> Unit,
    ) {
        bytes(field, ProtoWriter().apply(block).toByteArray())
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun tag(
        field: Int,
        wireType: Int,
    ) {
        require(isValidProtobufFieldNumber(field.toLong())) { "Invalid protobuf field number $field" }
        varint((field.toLong() shl 3) or wireType.toLong())
    }

    private fun varint(input: Long) {
        var value = input
        while (true) {
            if ((value and -0x80L) == 0L) {
                append(value.toByte())
                return
            }
            append(((value and 0x7fL) or 0x80L).toByte())
            value = value ushr 7
        }
    }

    private fun fixed32(value: Int) {
        append(value.toByte())
        append((value ushr 8).toByte())
        append((value ushr 16).toByte())
        append((value ushr 24).toByte())
    }

    private fun append(value: Byte) {
        ensureCapacity(1)
        buffer[size++] = value
    }

    private fun append(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(buffer, destinationOffset = size)
        size += value.size
    }

    private fun ensureCapacity(extra: Int) {
        val required = size + extra
        if (required <= buffer.size) return
        var capacity = buffer.size
        while (capacity < required) capacity = (capacity * 2).coerceAtLeast(required)
        buffer = buffer.copyOf(capacity)
    }

    private companion object {
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5
    }
}

internal class ProtoReader(
    private val data: ByteArray,
) {
    var position: Int = 0
        private set

    val hasRemaining: Boolean
        get() = position < data.size

    fun tag(): ProtoTag {
        val raw = varint()
        val field = raw ushr 3
        if (!isValidProtobufFieldNumber(field)) throw SabrProtocolException("Invalid protobuf field number $field")
        return ProtoTag(field = field.toInt(), wireType = (raw and 7).toInt())
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        repeat(10) {
            if (position >= data.size) throw SabrProtocolException("Truncated protobuf varint")
            val byte = data[position++].toInt() and 0xff
            if (shift == 63 && ((byte and 0x7f) > 1 || (byte and 0x80) != 0)) {
                throw SabrProtocolException("Protobuf varint overflows 64 bits")
            }
            result = result or ((byte and 0x7f).toLong() shl shift)
            if ((byte and 0x80) == 0) return result
            shift += 7
        }
        throw SabrProtocolException("Protobuf varint is too long")
    }

    fun bool(): Boolean {
        val value = varint()
        if (value < 0) throw SabrProtocolException("Invalid protobuf boolean $value")
        return value != 0L
    }

    fun bytes(): ByteArray {
        val length = varint()
        if (length < 0 || length > Int.MAX_VALUE) throw SabrProtocolException("Invalid protobuf length $length")
        val end = position + length.toInt()
        if (end < position || end > data.size) throw SabrProtocolException("Truncated protobuf field")
        return data.copyOfRange(position, end).also { position = end }
    }

    fun string(): String = bytes().decodeToString()

    fun skip(tag: ProtoTag) {
        when (tag.wireType) {
            0 -> varint()
            1 -> advance(8)
            2 -> advanceLengthDelimited()
            5 -> advance(4)
            else -> throw SabrProtocolException("Unsupported protobuf wire type ${tag.wireType}")
        }
    }

    private fun advanceLengthDelimited() {
        val length = varint()
        if (length < 0 || length > Int.MAX_VALUE) throw SabrProtocolException("Invalid protobuf length $length")
        advance(length.toInt())
    }

    private fun advance(count: Int) {
        val end = position + count
        if (end < position || end > data.size) throw SabrProtocolException("Truncated protobuf field")
        position = end
    }
}

internal data class ProtoTag(
    val field: Int,
    val wireType: Int,
)
