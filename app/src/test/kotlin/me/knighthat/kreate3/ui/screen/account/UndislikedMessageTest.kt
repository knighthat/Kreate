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
package me.knighthat.kreate3.ui.screen.account

import me.knighthat.kreate3.notification.MessageDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UndislikedMessageTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun noSongSaysNothing() = assertNull(undislikedMessage(resources, emptyList()))

    @Test
    fun oneSongDoneSaysSo() =
        assertEquals("Removed from dislike", undislikedMessage(resources, listOf(null))?.text)

    @Test
    fun oneSongFailedSaysWhy() {
        val message = undislikedMessage(resources, listOf("Can't reach YouTube"))
        assertEquals("Can't reach YouTube", message?.text)
        assertEquals(MessageDuration.LONG, message?.duration)
    }

    @Test
    fun severalSongsDoneAreCounted() = assertEquals(
        "3 removed from dislike",
        undislikedMessage(resources, listOf(null, null, null))?.text,
    )

    @Test
    fun severalSongsCountTheFailedOnes() {
        val message = undislikedMessage(resources, listOf(null, "refused", null, "unreachable"))
        assertEquals("2 removed from dislike, 2 failed", message?.text)
        assertEquals(MessageDuration.LONG, message?.duration)
    }

    @Test
    fun severalSongsAllFailed() = assertEquals(
        "0 removed from dislike, 2 failed",
        undislikedMessage(resources, listOf("refused", "refused"))?.text,
    )
}
