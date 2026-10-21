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

package me.knighthat.kreate3.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongSubtitleTest {

    @Test
    fun artistsComeBeforeTheAlbum() =
        assertEquals(
            "Daft Punk, Pharrell • Random Access Memories",
            songSubtitle("Daft Punk, Pharrell", "Random Access Memories")
        )

    @Test
    fun aMissingPartIsLeftOut() {
        assertEquals("Daft Punk", songSubtitle("Daft Punk", null))
        assertEquals("Discovery", songSubtitle(null, "Discovery"))
        assertEquals("Discovery", songSubtitle("  ", "Discovery"))
    }

    @Test
    fun nothingKnownGivesNoSubtitle() {
        assertNull(songSubtitle(null, null))
        assertNull(songSubtitle("", " "))
    }
}
