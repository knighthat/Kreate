package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VideoFormatSelectorTest {
    private val formats =
        listOf(
            Format(
                itag = 136,
                mimeType = "video/mp4; codecs=\"avc1\"",
                bitrate = 1_000_000,
                width = 1280,
                height = 720,
                url = "https://720",
            ),
            Format(
                itag = 247,
                mimeType = "video/webm; codecs=\"vp9\"",
                bitrate = 2_000_000,
                width = 1920,
                height = 1080,
                url = "https://1080",
            ),
            Format(
                itag = 137,
                mimeType = "video/mp4; codecs=\"avc1\"",
                bitrate = 4_000_000,
                width = 2560,
                height = 1440,
                url = "https://1440",
            ),
        )

    @Test
    fun selectsHighestHeightWithinCapAndDegradesAboveCap() {
        assertEquals(137, selectBestVideoFormat(formats)?.itag)
        assertEquals(247, selectBestVideoFormat(formats, maxHeight = 1080)?.itag)
        assertEquals(136, selectBestVideoFormat(formats, maxHeight = 720)?.itag)
        assertEquals(136, selectBestVideoFormat(formats, maxHeight = 144)?.itag)
    }

    @Test
    fun prefersLowerBitrateNativeVideoAtEqualHeight() {
        val equal =
            listOf(
                Format(
                    itag = 136,
                    mimeType = "video/mp4; codecs=\"avc1\"",
                    bitrate = 2_000_000,
                    width = 1280,
                    height = 720,
                    url = "https://mp4",
                ),
                Format(
                    itag = 247,
                    mimeType = "video/webm; codecs=\"vp9\"",
                    bitrate = 1_000_000,
                    width = 1280,
                    height = 720,
                    url = "https://webm",
                ),
            )
        assertEquals(247, selectBestVideoFormat(equal)?.itag)
        assertEquals(
            247,
            selectBestVideoFormat(
                equal +
                    Format(
                        itag = 299,
                        mimeType = "video/mp4; codecs=\"avc1\"",
                        bitrate = 5_000_000,
                        width = 1280,
                        height = 720,
                        url = "https://high",
                    ),
            )?.itag,
        )
    }

    @Test
    fun expensiveVp9DoesNotDisplaceCheaperAvcAtTheSameResolution() {
        val avc = formats.first().copy(bitrate = 2_474_373)
        val vp9 = formats[1].copy(width = 1280, height = 720, bitrate = 7_281_188)
        val av1 = avc.copy(itag = 398, mimeType = "video/mp4; codecs=\"av01\"", bitrate = 1_000_000)
        assertEquals(avc.itag, selectBestVideoFormat(listOf(vp9, avc, av1))?.itag)
        assertEquals(vp9.itag, selectBestVideoFormat(listOf(avc, vp9.copy(bitrate = 1_000_000)))?.itag)
        assertEquals(vp9.itag, selectBestVideoFormat(listOf(avc.copy(bitrate = 0), vp9))?.itag)
    }

    @Test
    fun filtersMissingDimensionsAndUrls() {
        val missing = listOf(Format(itag = 247, mimeType = "video/webm", bitrate = 2_000_000, width = 1920, height = 1080), formats.first())
        assertEquals(136, selectBestVideoFormat(missing)?.itag)
        assertEquals(247, selectBestVideoFormat(missing, requireUrl = false)?.itag)
        assertNull(selectBestVideoFormat(listOf(Format(itag = 1, mimeType = "video/mp4", bitrate = 1, width = 1, height = 1))))
    }

    @Test
    fun manualCapFallsBackToLowestAvailableResolution() {
        val result =
            selectBestVideoFormat(
                listOf(
                    Format(itag = 313, mimeType = "video/webm", bitrate = 10_000_000, width = 3840, height = 2160, url = "https://2160"),
                    Format(itag = 308, mimeType = "video/webm", bitrate = 20_000_000, width = 7680, height = 4320, url = "https://4320"),
                ),
                maxHeight = 1080,
            )

        assertNotNull(result)
        assertEquals(313, result.itag)
    }

    @Test
    fun adaptiveVideoBeatsCombinedFormatAtSameRequestedHeight() {
        val formats =
            listOf(
                Format(
                    itag = 22,
                    mimeType = "video/mp4; codecs=\"avc1, mp4a\"",
                    bitrate = 2_000_000,
                    width = 1280,
                    height = 720,
                    url = "https://combined",
                ),
                Format(
                    itag = 248,
                    mimeType = "video/webm; codecs=\"vp9\"",
                    bitrate = 3_000_000,
                    width = 1920,
                    height = 1080,
                    url = "https://adaptive",
                ),
            )

        assertEquals(248, selectBestVideoFormat(formats)?.itag)
    }
}
