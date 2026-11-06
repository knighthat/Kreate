package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.models.response.PlayerResponse.StreamingData.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class ExtractionFoundationTest {
    @Test
    fun videoSelectorHonorsHeightCapAndDegradesAboveCap() {
        val formats =
            listOf(
                Format(itag = 1, url = "https://example.test/1", mimeType = "video/mp4", width = 640, height = 360),
                Format(itag = 2, url = "https://example.test/2", mimeType = "video/webm", width = 1280, height = 720),
            )

        assertEquals(1, selectBestVideoFormat(formats, maxHeight = 480)?.itag)
        assertEquals(1, selectBestVideoFormat(formats, maxHeight = 144)?.itag)
    }

    @Test
    fun nonceMutationRejectsUnapprovedUrlsAndPreservesFragment() {
        val nonce = "abcdefghijklmnop"
        val approved = "https://r1.googlevideo.com/videoplayback?foo=bar#fragment"

        assertEquals(
            "https://r1.googlevideo.com/videoplayback?foo=bar&cpn=$nonce#fragment",
            appendClientPlaybackNonce(approved, nonce),
        )
        assertEquals(
            "https://example.com/videoplayback?foo=bar",
            appendClientPlaybackNonce("https://example.com/videoplayback?foo=bar", nonce),
        )
        assertEquals(
            "https://r1.googlevideo.com:8443/videoplayback?foo=bar",
            appendClientPlaybackNonce("https://r1.googlevideo.com:8443/videoplayback?foo=bar", nonce),
        )
        assertEquals(approved, replaceClientPlaybackNonce(approved, "short"))
    }

    @Test
    fun contentHintsDiagnosticDoesNotExposeEndpointParams() {
        val hints = ContentHints(endpointParams = "secret", maxVideoHeight = 720)

        assertEquals(false, hints.diagnosticSummary().contains("secret"))
        assertEquals(true, hints.diagnosticSummary().contains("endpointParamsPresent=true"))
    }

    @Test
    fun contentHintsCopyRetainsEntitlementAndCapabilities() {
        val copied =
            ContentHints()
                .withPremium()
                .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = false)
                .copy(wantVideo = true)

        assertTrue(copied.premium)
        assertTrue(copied.wantVideo)
        assertFalse(copied.allowHls)
        assertFalse(copied.allowSabr)
        assertFalse(copied.allowBoundedRange)
    }

    @Test
    fun invalidAudioSelectionReturnsNull() {
        assertNull(selectBestAudioFormat(emptyList()))
        val format = Format(itag = 1, url = "https://example.test", mimeType = "audio/mp4", bitrate = 1)
        assertSame(format, selectBestAudioFormat(listOf(format)))
    }

    @Test
    fun sensitiveModelsRedactValuesFromToString() {
        val config = PlayerConfig("https://www.youtube.com/s/player/hash/base.js", 12345, "visitor-secret", "1.0")
        val token = PoTokenResult("player-secret", "stream-secret", "visitor-secret")
        val tracking = PlaybackTrackingData("nonce-secret", "https://stats.test/playback", "https://stats.test/watch", null, null, 1)
        val bearer = TvBearerCredential("bearer-secret", "TVHTML5", Clock.System.now().plus(1.hours), 0, "visitor-secret")

        assertFalse(config.toString().contains("visitor-secret"))
        assertFalse(token.toString().contains("player-secret"))
        assertFalse(token.toString().contains("stream-secret"))
        assertFalse(bearer.toString().contains("bearer-secret"))
        assertFalse(tracking.toString().contains("stats.test"))
        assertFalse(tracking.toString().contains("nonce-secret"))
    }
}
