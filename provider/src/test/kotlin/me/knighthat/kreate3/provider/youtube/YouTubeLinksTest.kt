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

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class YouTubeLinksTest {

    @Test
    fun linksAreMadeFromIds() {
        assertEquals("https://music.youtube.com/watch?v=abc", YouTubeLinks.song("abc"))
        assertEquals("https://music.youtube.com/browse/MPREb_1", YouTubeLinks.album("MPREb_1"))
        assertEquals("https://music.youtube.com/channel/UC1", YouTubeLinks.artist("UC1"))
        assertEquals("https://music.youtube.com/playlist?list=PL1", YouTubeLinks.playlist("PL1"))
    }

    @Test
    fun coversAreCutToTheSizeAsked() {
        val cover = "https://yt3.googleusercontent.com/abc-DEF_1=w60-h60-l90-rj"

        assertEquals(
            "https://yt3.googleusercontent.com/abc-DEF_1=w300-h300-p-nu-l80-rj",
            resizeArtwork(cover, 300)
        )
        assertEquals("https://yt3.googleusercontent.com/abc-DEF_1=s0", resizeArtwork(cover, null))
        // An address without a size gets one
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w120-h120-p-nu-l80-rj",
            resizeArtwork("https://lh3.googleusercontent.com/abc", 120),
        )
        assertEquals(
            "https://yt3.ggpht.com/abc=w88-h88-p-nu-l80-rj",
            resizeArtwork("https://yt3.ggpht.com/abc=s88-c-k-c0x00ffffff-no-rj", 88),
        )
    }

    @Test
    fun videoFramesTakeTheClosestSize() {
        val frame = "https://i.ytimg.com/vi/a5uQMwRMHcs/hq720.jpg?sqp=-oaymwEK&rs=AMzJL3m"

        assertEquals("https://i.ytimg.com/vi/a5uQMwRMHcs/mqdefault.jpg", resizeArtwork(frame, 200))
        assertEquals("https://i.ytimg.com/vi/a5uQMwRMHcs/hqdefault.jpg", resizeArtwork(frame, 600))
        assertEquals("https://i.ytimg.com/vi/a5uQMwRMHcs/hqdefault.jpg", resizeArtwork(frame, null))
    }

    @Test
    fun otherAddressesAreLeftAlone() {
        assertEquals("https://example.com/a.jpg", resizeArtwork("https://example.com/a.jpg", 100))
    }

    @Test
    fun durationsAreRead() {
        assertEquals(105.seconds, parseDuration("1:45"))
        assertEquals(3723.seconds, parseDuration("1:02:03"))
        assertEquals(null, parseDuration("44M plays"))
        assertEquals(null, parseDuration("2013"))
    }
}
