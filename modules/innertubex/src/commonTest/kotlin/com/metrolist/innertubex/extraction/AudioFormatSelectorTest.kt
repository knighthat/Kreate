package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AudioFormatSelectorTest {
    @Test
    fun prefersOriginalAudioTrackOverHigherBitrateDub() {
        val dub =
            Format(
                itag = 251,
                mimeType = "audio/webm; codecs=\"opus\"",
                bitrate = 156_852,
                url = "https://dub",
                xtags = "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICaWQ",
                audioTrack = Format.AudioTrack(id = "id.3", audioIsDefault = false),
            )
        val original =
            dub.copy(
                bitrate = 144_266,
                url = "https://original",
                xtags = "ChEKBWFjb250EghvcmlnaW5hbAoNCgRsYW5nEgVlbi1VUw",
                audioTrack = Format.AudioTrack(id = "en-US.4", audioIsDefault = false),
            )

        AudioQuality.entries.filter { it != AudioQuality.MP4 }.forEach {
            assertEquals("https://original", selectBestAudioFormat(listOf(dub, original), it)?.url)
        }
        val defaultOnly = original.copy(xtags = null, audioTrack = Format.AudioTrack(id = "en-US.4", audioIsDefault = true))
        assertEquals("https://original", selectBestAudioFormat(listOf(dub, defaultOnly))?.url)
        assertEquals("https://dub", selectBestAudioFormat(listOf(dub))?.url)
    }

    @Test
    fun prefersStereoAndHigherSampleRate() {
        val formats =
            listOf(
                Format(
                    itag = 251,
                    mimeType = "audio/webm; codecs=\"opus\"",
                    bitrate = 128_000,
                    audioChannels = 1,
                    audioSampleRate = 44_100,
                    url = "https://mono",
                ),
                Format(
                    itag = 250,
                    mimeType = "audio/webm; codecs=\"opus\"",
                    bitrate = 128_000,
                    audioChannels = 2,
                    audioSampleRate = 48_000,
                    url = "https://stereo",
                ),
            )

        assertEquals(250, selectBestAudioFormat(formats, AudioQuality.HIGH)?.itag)
        assertEquals(2, selectBestAudioFormat(formats, AudioQuality.HIGH)?.audioChannels)
    }

    @Test
    fun lowQualityPrefersLowestMp4ThenLowestBitrate() {
        val formats =
            listOf(
                Format(itag = 251, mimeType = "audio/webm", bitrate = 160_000, url = "https://opus"),
                Format(itag = 140, mimeType = "audio/mp4", bitrate = 128_000, url = "https://aac"),
                Format(itag = 250, mimeType = "audio/webm", bitrate = 70_000, url = "https://opus-low"),
            )

        assertEquals(140, selectBestAudioFormat(formats, AudioQuality.LOW)?.itag)
        assertEquals(250, selectBestAudioFormat(formats.filter { it.itag != 140 }, AudioQuality.LOW)?.itag)
    }

    @Test
    fun mp4RequiresAPlayerCompatibleFormat() {
        val formats =
            listOf(
                Format(itag = 251, mimeType = "audio/webm", bitrate = 160_000, url = "https://opus"),
                Format(itag = 140, mimeType = "audio/mp4", bitrate = 128_000, url = "https://aac"),
            )

        assertEquals(140, selectBestAudioFormat(formats, AudioQuality.MP4)?.itag)
        assertNull(selectBestAudioFormat(formats.take(1), AudioQuality.MP4))
    }

    @Test
    fun autoAndMp4AllowBitrateToOutweighStereoBonus() {
        val webmFormats =
            listOf(
                Format(
                    itag = 250,
                    mimeType = "audio/webm",
                    bitrate = 32_000,
                    audioChannels = 2,
                    url = "https://stereo.example.test/audio",
                ),
                Format(
                    itag = 251,
                    mimeType = "audio/webm",
                    bitrate = 256_000,
                    audioChannels = 1,
                    url = "https://mono.example.test/audio",
                ),
            )
        val mp4Formats =
            listOf(
                Format(
                    itag = 140,
                    mimeType = "audio/mp4",
                    bitrate = 32_000,
                    audioChannels = 2,
                    url = "https://stereo.example.test/audio",
                ),
                Format(
                    itag = 141,
                    mimeType = "audio/mp4",
                    bitrate = 256_000,
                    audioChannels = 1,
                    url = "https://mono.example.test/audio",
                ),
            )

        assertEquals(251, selectBestAudioFormat(webmFormats, AudioQuality.AUTO)?.itag)
        assertEquals(141, selectBestAudioFormat(mp4Formats, AudioQuality.MP4)?.itag)
    }

    @Test
    fun weightedAudioRankingDoesNotOverflowExtremeBitrate() {
        val formats =
            listOf(
                Format(
                    itag = 251,
                    mimeType = "audio/webm",
                    bitrate = Int.MAX_VALUE,
                    audioChannels = 1,
                    url = "https://max.example.test/audio",
                ),
                Format(
                    itag = 250,
                    mimeType = "audio/webm",
                    bitrate = 256_000,
                    audioChannels = 2,
                    url = "https://stereo.example.test/audio",
                ),
            )

        assertEquals(251, selectBestAudioFormat(formats, AudioQuality.AUTO)?.itag)
    }

    @Test
    fun highQualityUsesBitrateBeforeContainer() {
        val formats =
            listOf(
                Format(
                    itag = 251,
                    mimeType = "audio/webm; codecs=\"opus\"",
                    bitrate = 128_000,
                    audioChannels = 2,
                    url = "https://opus.example.test/audio",
                ),
                Format(
                    itag = 141,
                    mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                    bitrate = 256_000,
                    audioChannels = 2,
                    url = "https://aac.example.test/audio",
                ),
            )

        assertEquals(141, selectBestAudioFormat(formats, AudioQuality.HIGH)?.itag)
        assertEquals(251, selectBestAudioFormat(formats, AudioQuality.AUTO)?.itag)
        assertEquals(141, selectBestAudioFormat(formats, AudioQuality.MP4)?.itag)
    }

    @Test
    fun highQualityKeepsHigherBitrateWithUnknownChannelMetadata() {
        val formats =
            listOf(
                Format(
                    itag = 141,
                    mimeType = "audio/mp4",
                    bitrate = 256_000,
                    audioChannels = null,
                    url = "https://high.example.test/audio",
                ),
                Format(
                    itag = 251,
                    mimeType = "audio/webm",
                    bitrate = 128_000,
                    audioChannels = 2,
                    url = "https://stereo.example.test/audio",
                ),
            )

        assertEquals(141, selectBestAudioFormat(formats, AudioQuality.HIGH)?.itag)
    }

    @Test
    fun highQualityComparatorDoesNotOverflowExtremeMetadata() {
        val formats =
            listOf(
                Format(
                    itag = 141,
                    mimeType = "audio/mp4",
                    bitrate = Int.MAX_VALUE,
                    audioChannels = null,
                    audioSampleRate = Int.MAX_VALUE,
                    url = "https://max.example.test/audio",
                ),
                Format(
                    itag = 251,
                    mimeType = "audio/webm",
                    bitrate = Int.MAX_VALUE - 1,
                    audioChannels = 2,
                    audioSampleRate = 48_000,
                    url = "https://bounded.example.test/audio",
                ),
            )

        assertEquals(141, selectBestAudioFormat(formats, AudioQuality.HIGH)?.itag)
    }

    @Test
    fun autoPrefersOpusAndUrlRequirementIsHonored() {
        val formats =
            listOf(
                Format(itag = 251, mimeType = "audio/webm", bitrate = 160_000),
                Format(itag = 140, mimeType = "audio/mp4", bitrate = 128_000, url = "https://aac"),
            )

        assertEquals(140, selectBestAudioFormat(formats, AudioQuality.HIGH)?.itag)
        assertEquals(251, selectBestAudioFormat(formats, AudioQuality.HIGH, requireUrl = false)?.itag)
        assertEquals(140, selectBestAudioFormat(formats, AudioQuality.AUTO)?.itag)
    }

    @Test
    fun autoFallsBackToBestNonWebmFormat() {
        val formats =
            listOf(
                Format(itag = 140, mimeType = "audio/mp4", bitrate = 128_000, url = "https://aac"),
                Format(itag = 141, mimeType = "audio/mp4", bitrate = 256_000, url = "https://aac-high"),
            )

        assertEquals(141, selectBestAudioFormat(formats, AudioQuality.AUTO)?.itag)
    }

    @Test
    fun emptyFormatsReturnNull() {
        assertNull(selectBestAudioFormat(emptyList(), AudioQuality.AUTO))
        assertNotNull(selectBestAudioFormat(listOf(Format(itag = 1, mimeType = "audio/mp4", bitrate = 1, url = "https://audio"))))
    }
}
