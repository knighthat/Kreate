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


package me.knighthat.kreate.database.paging

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {

    @Test
    fun textIsComparedIgnoringCase() {
        val order = SortOrder(UserSongSort.NAME)

        assertEquals(
            "ORDER BY name COLLATE NOCASE ASC NULLS LAST, song_id",
            order.toOrderBy("song_id"),
        )
    }

    @Test
    fun numbersAreComparedAsTheyAre() {
        val order = SortOrder(UserSongSort.DURATION, SortDirection.DESCENDING)

        assertEquals("ORDER BY duration DESC NULLS LAST, song_id", order.toOrderBy("song_id"))
    }

    @Test
    fun columnsOfSongsInPlaylistsAreQualified() {
        val order = SortOrder(SongSort.ALBUM_POSITION)

        assertEquals(
            "ORDER BY song.album_position ASC NULLS LAST, song.id",
            order.toOrderBy("song.id")
        )
    }

    @Test
    fun everyFieldHasAUniqueColumn() {
        listOf(
            UserSongSort.entries, UserAlbumSort.entries, UserArtistSort.entries,
            PlaylistSort.entries, SongSort.entries, SearchSort.entries,
        ).forEach { fields ->
            assertEquals(fields.size, fields.map { it.column }.toSet().size)
        }
    }
}
