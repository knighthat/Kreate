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

package me.knighthat.kreate3.source

import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playlist.parsePlaylistCsv
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import kotlin.time.Duration.Companion.seconds

class PlaylistFilesTest {

    private val device = ExportedSong(
        id = "42",
        origin = Origin.LOCAL,
        title = "Song, with \"quotes\"",
        artists = listOf("First", "Second"),
        artistIds = listOf("a1", "a2"),
        albumId = "al1",
        albumName = "Album, the first",
        albumYear = 2020,
        albumPosition = 3,
        duration = 212.seconds,
        thumbnail = "content://media/external/audio/albumart/7",
    )
    private val youTube = ExportedSong(
        id = "dQw4w9WgXcQ",
        origin = Origin.YOUTUBE,
        title = "Two\nlines",
        artists = emptyList(),
        duration = null,
        thumbnail = "https://lh3.googleusercontent.com/cover=w226-h226",
    )
    private val server = ExportedSong(
        id = "srv1",
        origin = Origin.OPEN_SUBSONIC,
        title = "Plain",
        artists = listOf("Band"),
        duration = 61.seconds,
        thumbnail = null,
    )
    private val songs = listOf(device, youTube, server)

    private fun csv(origins: Set<Origin> = Origin.entries.toSet()) =
        StringBuilder().also {
            PlaylistFiles.writeCsv(it, "Mix, vol. 1", songs, origins)
        }.toString()

    @Test
    fun csvStartsWithTheHeader() {
        assertEquals(
            "playlist_remote_id,playlist_name,song_id,name,artists,artist_ids,album_id," +
                    "album_name,album_year,album_position,duration,thumbnail,origin",
            csv().lines().first()
        )
    }

    @Test
    fun csvQuotesFieldsWithCommasQuotesAndLineBreaks() {
        val rows = csv().split("\r\n")

        assertEquals(
            ",\"Mix, vol. 1\",42,\"Song, with \"\"quotes\"\"\",\"First, Second\",a1|a2,al1," +
                    "\"Album, the first\",2020,3,212," +
                    "content://media/external/audio/albumart/7,LOCAL",
            rows[1]
        )
        assertEquals(
            ",\"Mix, vol. 1\",dQw4w9WgXcQ,\"Two\nlines\",,,,,,,," +
                    "https://lh3.googleusercontent.com/cover=w226-h226,YOUTUBE",
            rows[2]
        )
        assertEquals(",\"Mix, vol. 1\",srv1,Plain,Band,,,,,,61,,OPEN_SUBSONIC", rows[3])
        // Every row ends with a line break, the last one too
        assertEquals("", rows.last())
        assertEquals(5, rows.size)
    }

    @Test
    fun csvLeavesOutTheUncheckedOrigins() {
        val rows = csv(setOf(Origin.OPEN_SUBSONIC)).split("\r\n").filter { it.isNotEmpty() }

        assertEquals(2, rows.size)
        assertEquals(",\"Mix, vol. 1\",srv1,Plain,Band,,,,,,61,,OPEN_SUBSONIC", rows[1])
    }

    @Test
    fun csvReadsBackAsWritten() {
        val rows = parsePlaylistCsv(StringReader(csv()))

        assertEquals(listOf("42", "dQw4w9WgXcQ", "srv1"), rows.map { it.song.id })
        val first = rows.first().song
        assertEquals(listOf("a1", "a2"), first.artistIds)
        assertEquals("Album, the first", first.albumName)
        assertEquals(3, first.albumPosition)
        assertEquals(Origin.LOCAL, first.origin)
    }

    @Test
    fun csvFieldIsLeftAsItIsWithoutSpecialCharacters() {
        assertEquals("plain text", PlaylistFiles.csvField("plain text"))
        assertEquals("\"a\rb\"", PlaylistFiles.csvField("a\rb"))
    }
}
