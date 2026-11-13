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

package me.knighthat.kreate3.database.repository

import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.DatabaseTest
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

internal class PlaylistImportRepositoryTest : DatabaseTest() {

    private lateinit var repository: PlaylistImportRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        addUser()
        repository = PlaylistImportRepositoryImpl(
            database,
            database.songDao(),
            database.userSongDao(),
            database.userPlaylistDao(),
            activeUser,
            clock,
        )
    }

    private fun imported(id: String, origin: Origin = Origin.YOUTUBE, name: String = id) =
        Song(id, origin, name = name, duration = 100.seconds)

    private suspend fun positions(playlistId: Long): List<String> =
        database.userPlaylistDao().songLinks(playlistId).sortedBy { it.position }.map { it.songId }

    @Test
    fun newSongsAreStoredWithoutUserValuesAndJoinTheLibrary() = runBlocking {
        val result = repository.importPlaylist("Mix", listOf(imported("y1")))

        val playlistId = result.playlistId!!
        assertEquals(1, result.added)
        assertEquals("Mix", database.userPlaylistDao().get(playlistId)?.name)
        assertEquals(Origin.LOCAL, database.userPlaylistDao().get(playlistId)?.origin)
        assertEquals(listOf("y1"), positions(playlistId))
        assertEquals("y1", database.songDao().get("y1")?.name)
        assertNotNull(database.userSongDao().get(USER, "y1"))
        assertNull(database.userSongDao().get(USER, "y1")?.artists)
    }

    @Test
    fun storedSongsKeepTheirValues() = runBlocking {
        addSong("y1", Origin.YOUTUBE, name = "Stored")

        val result = repository.importPlaylist("Mix", listOf(imported("y1", name = "From file")))

        assertEquals(1, result.added)
        assertEquals("Stored", database.songDao().get("y1")?.name)
        assertNotNull(database.userSongDao().get(USER, "y1"))
        assertNull(database.userSongDao().get(USER, "y1")?.artists)
    }

    @Test
    fun deviceSongsNotStoredAndOtherOriginsAndRepeatsAreLeftOut() = runBlocking {
        addSong("1", Origin.LOCAL)
        addSong("taken", Origin.OPEN_SUBSONIC)

        val result = repository.importPlaylist(
            "Mix",
            listOf(
                imported("1", Origin.LOCAL),
                imported("2", Origin.LOCAL),
                imported("taken", Origin.YOUTUBE),
                imported("y1"),
                imported("1", Origin.LOCAL),
            ),
        )

        assertEquals(listOf("2", "taken", "1"), result.leftOut)
        assertEquals(listOf("1", "y1"), positions(result.playlistId!!))
        assertNull(database.songDao().get("2"))
    }

    @Test
    fun noPlaylistWhenEverySongIsLeftOut() = runBlocking {
        val result = repository.importPlaylist("Empty", listOf(imported("9", Origin.LOCAL)))

        assertNull(result.playlistId)
        assertEquals(0, result.added)
        assertEquals(listOf("9"), result.leftOut)
        Unit
    }

    @Test
    fun storedSongsLeaveOutUnavailableOnes() = runBlocking {
        addSong("1", Origin.LOCAL)
        addSong("2", Origin.LOCAL, unavailableSince = EPOCH)
        addSong("y1", Origin.YOUTUBE)

        assertEquals(listOf("1"), repository.storedSongs(Origin.LOCAL).map { it.id })
    }
}
