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
package me.knighthat.kreate3.playback

import me.knighthat.kreate3.provider.youtube.YouTubeQueueStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueTailTest {

    @Test
    fun aTailReadsBackAsItWasSaved() {
        val tail = QueueTail.of(YouTubeQueueStart("RDCLAK5uy", "wAEB8gECGAE%3D"), "CBkSYhIL")

        assertEquals(tail, QueueTail.decode(tail.encode()))
        assertEquals(YouTubeQueueStart("RDCLAK5uy", "wAEB8gECGAE%3D"), tail.start)
    }

    @Test
    fun aTailThatCantBeReadIsNone() {
        assertNull(QueueTail.decode(null))
        assertNull(QueueTail.decode("not a tail"))
        assertNull(QueueTail.decode("""{"playlistId":"PL"}"""))
    }
}
