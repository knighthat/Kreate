/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.knighthat.kreate3.provider.youtube

import com.metrolist.innertubex.extraction.ExtractedStream
import me.knighthat.kreate3.database.model.SongFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/** What InnerTubeX finds, as the player gets it. */
class YouTubeStreamsTest {

    private fun extracted(
        mimeType: String? = "audio/webm",
        codecs: String? = "opus",
        requireBoundedRange: Boolean = false,
    ) = ExtractedStream(
        videoId = "J7p4bzqLvCw",
        audioUrl = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=251",
        headers = mapOf("User-Agent" to "test"),
        loudnessDb = 7.29,
        expiresAt = Instant.fromEpochSeconds(1_800_000_000),
        contentLengthBytes = 3_350_747,
        itag = 251,
        mimeType = mimeType,
        codecs = codecs,
        bitrate = 143_381,
        sampleRate = 48_000,
        clientName = "VISIONOS",
        profileId = "VISIONOS_0_1__nopo",
        requireBoundedRange = requireBoundedRange,
        rangeChunkSizeBytes = 524_288,
    )

    @Test
    fun streamKeepsWhatThePlayerNeeds() {
        val stream = extracted().toYouTubeStream()!!

        assertEquals("https://rr1---sn-test.googlevideo.com/videoplayback?itag=251", stream.url)
        assertEquals(mapOf("User-Agent" to "test"), stream.headers)
        assertEquals("audio/webm; codecs=\"opus\"", stream.mimeType)
        assertEquals(143_381, stream.bitrate)
        assertEquals(3_350_747L, stream.contentLength)
        assertEquals(7.29, stream.loudnessDb!!, 0.0)
        assertEquals(Instant.fromEpochSeconds(1_800_000_000), stream.expiresAt)
        // Any range is answered, the chunk size is only a hint then
        assertNull(stream.rangeSize)
        assertEquals("VISIONOS_0_1__nopo", stream.client)
        assertEquals(251, stream.itag)
    }

    @Test
    fun formatIsTaggedWithItsItagAndTakesTheLoudnessAsGain() {
        assertEquals(
            SongFormat(
                "J7p4bzqLvCw",
                "251",
                "audio/webm; codecs=\"opus\"",
                143_381,
                3_350_747,
                7.29f
            ),
            extracted().toYouTubeStream()!!.toSongFormat("J7p4bzqLvCw"),
        )
    }

    @Test
    fun boundedRangesComeWithTheirSize() {
        assertEquals(524_288L, extracted(requireBoundedRange = true).toYouTubeStream()!!.rangeSize)
    }

    @Test
    fun mimeTypeWithoutCodecsIsKeptAsIs() {
        assertEquals("audio/mp4", extracted("audio/mp4", null).toYouTubeStream()!!.mimeType)
        assertNull(extracted(null, "opus").toYouTubeStream()!!.mimeType)
    }
}
