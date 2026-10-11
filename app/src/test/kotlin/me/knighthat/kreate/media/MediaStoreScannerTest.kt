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

package me.knighthat.kreate.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class MediaStoreScannerTest {

    private fun row(
        title: String? = "Title",
        durationMs: Long = 215_000,
        track: Int = 3,
        year: Int = 2020,
        artist: String? = "Artist",
        album: String? = "Album",
    ) = toLocalSong(
        id = 7, title = title, durationMs = durationMs, track = track, year = year,
        artistId = 11, artist = artist, albumId = 13, album = album, albumArt = null,
    )

    @Test
    fun knownValuesAreKept() {
        val song = row()

        assertEquals("7", song.id)
        assertEquals("Title", song.name)
        assertEquals(215_000.milliseconds, song.duration)
        assertEquals(3, song.albumPosition)
        assertEquals("11", song.artist?.id)
        assertEquals("Artist", song.artist?.name)
        assertEquals("13", song.album?.id)
        assertNull(song.album?.thumbnail)
        assertEquals(2020, song.album?.year)
    }

    @Test
    fun discNumberIsDroppedFromTrack() {
        assertEquals(3, row(track = 1003).albumPosition)
        assertNull(row(track = 0).albumPosition)
        assertNull(row(track = 2000).albumPosition)
    }

    @Test
    fun unknownTextAndZeroNumbersBecomeNull() {
        val song =
            row(title = "<unknown>", durationMs = 0, year = 0, artist = "<unknown>", album = "  ")

        assertNull(song.name)
        assertNull(song.duration)
        assertNull(song.artist)
        assertNull(song.album)
    }

    @Test
    fun albumKeepsItselfWithoutYear() {
        assertNull(row(year = 0).album?.year)
    }
}
