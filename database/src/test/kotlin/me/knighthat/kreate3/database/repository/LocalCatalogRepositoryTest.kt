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

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.DatabaseTest
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.model.LocalAlbum
import me.knighthat.kreate3.database.model.LocalArtist
import me.knighthat.kreate3.database.model.LocalSong
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class LocalCatalogRepositoryTest : DatabaseTest() {

    private lateinit var repository: LocalCatalogRepositoryImpl

    private fun song(
        id: String,
        name: String? = id,
        artist: String? = null,
        album: String? = null,
    ) = LocalSong(
        id = id,
        name = name,
        artist = artist?.let { LocalArtist("artist:$it", it) },
        album = album?.let { LocalAlbum("album:$it", it) },
    )

    @Before
    fun setUp() = runBlocking {
        addUser()
        repository = LocalCatalogRepositoryImpl(
            database,
            database.songDao(),
            database.albumDao(),
            database.artistDao(),
            database.userSongDao(),
            database.userAlbumDao(),
            database.userArtistDao(),
            activeUser,
            clock,
        )
    }

    @Test
    fun aScanStoresSongsAlbumsArtistsAndTheirLibraryRows() = runBlocking {
        repository.sync(listOf(song("s1", artist = "A", album = "X")))

        val stored = database.songDao().get("s1")!!
        assertEquals(Origin.LOCAL, stored.origin)
        assertEquals("album:X", stored.albumId)
        assertEquals("A", database.artistDao().get("artist:A")?.name)
        assertEquals(listOf("artist:A"), database.songDao().artistsOf("s1").first().map { it.id })
        assertNotNull(database.userSongDao().get(USER, "s1"))
        assertNotNull(database.userAlbumDao().get(USER, "album:X"))
        assertNotNull(database.userArtistDao().get(USER, "artist:A"))
    }

    @Test
    fun anAlbumIsByEveryArtistOfItsSongsOnce() = runBlocking {
        repository.sync(
            listOf(
                song("s1", artist = "A", album = "X"),
                song("s2", artist = "B", album = "X"),
                song("s3", artist = "A", album = "X"),
                song("s4", album = "X"),
                song("s5", album = "Y"),
            )
        )

        assertEquals(listOf("A", "B"), database.albumDao().get("album:X")?.artists)
        assertNull(database.albumDao().get("album:Y")?.artists)
    }

    @Test
    fun anUnchangedSongIsNotWrittenAgain() = runBlocking {
        val created = clock.now()
        repository.sync(listOf(song("s1", artist = "A", album = "X")))
        clock.advance()

        repository.sync(listOf(song("s1", artist = "A", album = "X")))

        assertEquals(created, database.songDao().get("s1")?.updatedAt)
        assertEquals(created, database.albumDao().get("album:X")?.updatedAt)
        assertEquals(created, database.artistDao().get("artist:A")?.updatedAt)
    }

    @Test
    fun aChangedSongKeepsWhenItWasFirstFound() = runBlocking {
        val created = clock.now()
        repository.sync(listOf(song("s1")))
        clock.advance()

        repository.sync(listOf(song("s1", name = "Retagged")))

        val stored = database.songDao().get("s1")!!
        assertEquals("Retagged", stored.name)
        assertEquals(created, stored.createdAt)
        assertEquals(clock.now(), stored.updatedAt)
    }

    @Test
    fun theArtistOfASongFollowsItsTags() = runBlocking {
        repository.sync(listOf(song("s1", artist = "A")))

        repository.sync(listOf(song("s1", artist = "B")))

        assertEquals(listOf("artist:B"), database.songDao().artistsOf("s1").first().map { it.id })
    }

    @Test
    fun theUsersOwnValuesSurviveARescan() = runBlocking {
        repository.sync(listOf(song("s1")))
        val liked = clock.now()
        database.userSongDao().upsert(
            UserSongEntity(
                USER,
                "s1",
                name = "Mine",
                likedAt = liked,
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        repository.sync(listOf(song("s1", name = "Retagged")))

        val library = database.userSongDao().get(USER, "s1")!!
        assertEquals("Mine", library.name)
        assertEquals(liked, library.likedAt)
    }

    @Test
    fun aSongTheScanMissedIsKeptAsUnavailableFromTheFirstMiss() = runBlocking {
        repository.sync(listOf(song("s1"), song("s2")))
        clock.advance()
        val firstMiss = clock.now()

        repository.sync(listOf(song("s1")))
        clock.advance()
        repository.sync(listOf(song("s1")))

        assertEquals(firstMiss, database.songDao().get("s2")?.unavailableSince)
        assertNull(database.songDao().get("s1")?.unavailableSince)
        // Its library row stays, the card it is on may only be out
        assertNotNull(database.userSongDao().get(USER, "s2"))
    }

    @Test
    fun aSongThatIsFoundAgainIsAvailable() = runBlocking {
        repository.sync(listOf(song("s1"), song("s2")))
        repository.sync(listOf(song("s1")))

        repository.sync(listOf(song("s1"), song("s2")))

        assertNull(database.songDao().get("s2")?.unavailableSince)
    }

    @Test
    fun songsOfOtherOriginsAreNotMarkedUnavailable() = runBlocking {
        addSong("remote", origin = Origin.OPEN_SUBSONIC)

        repository.sync(listOf(song("s1")))

        assertNull(database.songDao().get("remote")?.unavailableSince)
    }

    @Test
    fun anEmptyScanChangesNothing() = runBlocking {
        repository.sync(listOf(song("s1")))

        repository.sync(emptyList())

        assertNull(database.songDao().get("s1")?.unavailableSince)
    }

    @Test
    fun aScanWaitsForTheActiveUser() = runBlocking {
        activeUser.id.value = null

        val sync = async { repository.sync(listOf(song("s1"))) }
        delay(200)
        assertFalse(sync.isCompleted)

        activeUser.id.value = USER
        sync.await()
        assertNotNull(database.userSongDao().get(USER, "s1"))
    }
}
