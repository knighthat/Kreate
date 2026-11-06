package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format
import com.metrolist.innertubex.sabr.ProtoReader
import kotlin.io.encoding.Base64

public fun selectBestAudioFormat(
    formats: List<Format>,
    audioQuality: AudioQuality = AudioQuality.AUTO,
    requireUrl: Boolean = true,
): Format? {
    val validFormats = originalAudioTrack(if (requireUrl) formats.filter { !it.url.isNullOrBlank() } else formats)
    if (validFormats.isEmpty()) return null
    return when (audioQuality) {
        AudioQuality.LOW -> {
            validFormats.filter { it.mimeType.contains("audio/mp4") }.minByOrNull { it.bitrate }
                ?: validFormats.minByOrNull { it.bitrate }
        }

        AudioQuality.AUTO -> {
            validFormats.filter { it.mimeType.contains("audio/webm") }.maxByOrNull(::audioFormatScore)
                ?: validFormats.maxByOrNull(::audioFormatScore)
        }

        AudioQuality.HIGH -> {
            validFormats.maxWithOrNull(highAudioFormatComparator)
        }

        AudioQuality.MP4 -> {
            validFormats.filter { it.mimeType.contains("audio/mp4") }.maxByOrNull(::audioFormatScore)
        }
    }
}

// Multi-language uploads expose one format per dubbed track; a dub can outrank the original by bitrate.
private fun originalAudioTrack(formats: List<Format>): List<Format> {
    if (formats.none { it.audioTrack != null }) return formats
    return formats
        .filter { it.audioContent() == "original" }
        .ifEmpty {
            formats.filter { it.audioTrack?.audioIsDefault == true }
        }.ifEmpty { formats }
}

// xtags is a base64url protobuf map of repeated {1: key, 2: value} entries, e.g. acont=original.
private fun Format.audioContent(): String? {
    val encoded = xtags?.takeIf { it.length <= MAX_XTAGS_LENGTH } ?: return null
    return runCatching {
        val reader = ProtoReader(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(encoded))
        while (reader.hasRemaining) {
            val tag = reader.tag()
            if (tag.field != 1 || tag.wireType != 2) {
                reader.skip(tag)
                continue
            }
            val entry = ProtoReader(reader.bytes())
            var key: String? = null
            var value: String? = null
            while (entry.hasRemaining) {
                val entryTag = entry.tag()
                when {
                    entryTag.field == 1 && entryTag.wireType == 2 -> key = entry.string()
                    entryTag.field == 2 && entryTag.wireType == 2 -> value = entry.string()
                    else -> entry.skip(entryTag)
                }
            }
            if (key == "acont") return@runCatching value
        }
        null
    }.getOrNull()
}

private const val MAX_XTAGS_LENGTH = 1024

public fun selectBestVideoFormat(
    formats: List<Format>,
    requireUrl: Boolean = true,
    maxHeight: Int = 2160,
): Format? {
    val validFormats = if (requireUrl) formats.filter { !it.url.isNullOrBlank() } else formats
    val videoFormats = validFormats.filter { it.width != null && (it.height ?: 0) > 0 }
    if (videoFormats.isEmpty()) return null
    val withinCap = videoFormats.filter { it.height!! <= maxHeight }
    return withinCap.maxWithOrNull(videoFormatComparator)
        ?: videoFormats
            .groupBy { it.height!! }
            .minByOrNull { it.key }
            ?.value
            ?.maxWithOrNull(videoFormatComparator)
}

private fun audioFormatScore(format: Format): Long {
    val codecRank =
        when {
            format.mimeType.contains("audio/webm") -> 100
            format.mimeType.contains("audio/mp4") -> 50
            else -> 0
        }
    val channelBonus =
        when (format.audioChannels) {
            2 -> 50_000
            1 -> 0
            else -> 25_000
        }
    return codecRank * 1_000_000L +
        channelBonus +
        format.bitrate.toLong() +
        (format.audioSampleRate ?: 0).coerceIn(0, 48_000) / 10
}

private val highAudioFormatComparator =
    compareBy<Format> { it.bitrate }
        .thenBy { it.audioChannelRank() }
        .thenBy { (it.audioSampleRate ?: 0).coerceIn(0, 48_000) }
        .thenBy { it.audioContainerRank() }

private fun Format.audioChannelRank(): Int =
    when (audioChannels) {
        2 -> 2
        null -> 1
        else -> 0
    }

private fun Format.audioContainerRank(): Int =
    when {
        mimeType.contains("audio/webm") -> 2
        mimeType.contains("audio/mp4") -> 1
        else -> 0
    }

// AVC and VP9 both have broad native decoding support; avoid excess bitrate at the same resolution.
private val videoFormatComparator =
    compareBy<Format> { it.height ?: 0 }
        .thenBy {
            val codec = it.mimeType.lowercase()
            when {
                "av01" in codec -> 1
                "avc1" in codec || "vp09" in codec || "vp9" in codec || "video/mp4" in codec || "video/webm" in codec -> 2
                else -> 0
            }
        }.thenByDescending { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: Int.MAX_VALUE }
