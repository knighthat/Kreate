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
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
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
            database.albumDao(),
            database.artistDao(),
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
    fun newSongsGetTheFilesAlbumAndArtistsStoredOnesKeepTheirRows() = runBlocking {
        addArtist("a1", Origin.YOUTUBE, name = "Stored name")
        addSong("y2", Origin.YOUTUBE, name = "Stored song")
        val credits = ImportedCredits(
            album = Album("al1", Origin.YOUTUBE, name = "Album", year = 2020),
            artists = listOf(
                Artist("a2", Origin.YOUTUBE, name = "New"),
                Artist("a1", Origin.YOUTUBE, name = "File name"),
            ),
        )

        repository.importPlaylist(
            "Mix",
            listOf(imported("y1"), imported("y2")),
            mapOf("y1" to credits, "y2" to credits),
        )

        assertEquals("al1", database.songDao().get("y1")?.albumId)
        assertEquals("Album", database.albumDao().get("al1")?.name)
        assertEquals(2020, database.albumDao().get("al1")?.year)
        assertEquals("New", database.artistDao().get("a2")?.name)
        assertEquals("Stored name", database.artistDao().get("a1")?.name)
        assertEquals(
            listOf("a2", "a1"),
            database.songDao().sourceArtists(listOf("y1")).map { it.artistId },
        )
        // A song stored already keeps its row, credits too
        assertNull(database.songDao().get("y2")?.albumId)
        assertEquals(
            emptyList<String>(),
            database.songDao().sourceArtists(listOf("y2")).map { it.artistId },
        )
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
