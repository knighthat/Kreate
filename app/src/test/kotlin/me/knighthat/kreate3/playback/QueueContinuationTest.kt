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

import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeQueueStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueContinuationTest {

    @Test
    fun everyKindReadsBackAsItWasHandedOver() {
        val kinds = listOf(
            QueueContinuation.YouTubeNext("RDCLAK5uy", "wAEB8gECGAE%3D", null, "CBkSYhIL"),
            QueueContinuation.YouTubePlaylist("PL1", "4qmFsg"),
            QueueContinuation.YouTubeRadio("song", "CBkSYhIL"),
            QueueContinuation.Similar("song", Origin.OPEN_SUBSONIC),
        )

        for (kind in kinds)
            assertEquals(kind, QueueContinuation.decode(kind.encode()))
    }

    @Test
    fun theStartOfAYouTubeNextIsWhatItWasMadeFrom() {
        val next = QueueContinuation.YouTubeNext("RDCLAK5uy", "wAEB8gECGAE%3D", null, "CBkSYhIL")

        assertEquals(YouTubeQueueStart("RDCLAK5uy", "wAEB8gECGAE%3D"), next.start)
    }

    @Test
    fun whatCantBeReadIsNone() {
        assertNull(QueueContinuation.decode(null))
        assertNull(QueueContinuation.decode("not a continuation"))
        assertNull(QueueContinuation.decode("""{"type":"unknown"}"""))
    }

    @Test
    fun theStateAdvancesOnlyFromWhatItHolds() {
        val state = QueueContinuationState()
        val first = QueueContinuation.YouTubePlaylist("PL1", "a")
        val second = QueueContinuation.YouTubePlaylist("PL1", "b")
        state.set(first)

        // The queue was replaced meanwhile
        state.set(QueueContinuation.Similar("other", Origin.LOCAL))
        assertFalse(state.advance(first, second))
        assertEquals(QueueContinuation.Similar("other", Origin.LOCAL), state.current.value)

        state.set(first)
        assertTrue(state.advance(first, second))
        assertEquals(second, state.current.value)
        assertTrue(state.advance(second, null))
        assertNull(state.current.value)
    }

    @Test
    fun aQueueReplacedWithTheSongsTheContinuationWasGivenForKeepsIt() {
        val state = QueueContinuationState()
        val continuation = QueueContinuation.YouTubeRadio("song", "token")

        // The command may reach the service before the songs do
        state.set(continuation, readyAt = 50)
        state.replaced(50)
        assertEquals(continuation, state.current.value)

        // Any other queue, or an emptied one, goes on with nothing
        state.replaced(12)
        assertNull(state.current.value)
        state.set(continuation, readyAt = 50)
        state.replaced(0)
        assertNull(state.current.value)
    }
}
