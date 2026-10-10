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

package me.knighthat.kreate.database.repository

import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserPlaylist
import me.knighthat.kreate.database.model.UserSong
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MappersTest {

    private val created = Instant.fromEpochMilliseconds(1_000)
    private val updated = Instant.fromEpochMilliseconds(2_000)

    @Test
    fun songSurvivesRoundTrip() {
        val song = Song("id", Origin.YOUTUBE, "name", 200.seconds, true, "album", 3)

        val entity = song.toEntity(created, updated)

        assertEquals(created, entity.createdAt)
        assertEquals(updated, entity.updatedAt)
        assertEquals(song, entity.toModel())
    }

    @Test
    fun userSongKeepsOwnerAndOverrides() {
        val song = UserSong(songId = "song", name = "mine", artists = listOf("a", "b"))

        val entity = song.toEntity("user", created, updated)

        assertEquals("user", entity.userId)
        assertEquals(song, entity.toModel())
    }

    @Test
    fun playlistKeepsItsId() {
        val playlist = UserPlaylist(id = 7, origin = Origin.LOCAL, name = "mix")

        assertEquals(playlist, playlist.toEntity("user", created, updated).toModel())
    }
}
