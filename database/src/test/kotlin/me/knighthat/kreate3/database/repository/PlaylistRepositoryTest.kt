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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.DatabaseTest
import me.knighthat.kreate3.database.asList
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.paging.SongSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PlaylistRepositoryTest : DatabaseTest() {

    private lateinit var repository: PlaylistRepositoryImpl

    private fun playlist(name: String) = UserPlaylist(origin = Origin.LOCAL, name = name)

    @Before
    fun setUp() = runBlocking {
        addUser()
        addUser("other")
        repository = PlaylistRepositoryImpl(database, database.userPlaylistDao(), activeUser, clock)
    }

    private suspend fun othersPlaylist(): Long = database.userPlaylistDao().upsert(
        UserPlaylistEntity(
            userId = "other",
            origin = Origin.LOCAL,
            name = "Theirs",
            createdAt = EPOCH,
            updatedAt = EPOCH
        )
    )

    @Test
    fun aNewPlaylistGetsItsOwnId() = runBlocking {
        val first = repository.save(playlist("One"))
        val second = repository.save(playlist("Two"))

        assertNotEquals(0L, first)
        assertNotEquals(first, second)
        assertEquals("One", repository.find(first).first()?.name)
    }

    @Test
    fun savingAgainKeepsTheIdAndWhenItWasMade() = runBlocking {
        val created = clock.now()
        val id = repository.save(playlist("One"))
        clock.advance()

        val saved = repository.save(playlist("Renamed").copy(id = id))

        assertEquals(id, saved)
        val row = database.userPlaylistDao().get(id)!!
        assertEquals("Renamed", row.name)
        assertEquals(created, row.createdAt)
        assertEquals(clock.now(), row.updatedAt)
    }

    @Test
    fun anotherUsersPlaylistCannotBeSavedOver() {
        val theirs = runBlocking { othersPlaylist() }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.save(playlist("Mine").copy(id = theirs)) }
        }
    }

    @Test
    fun songsAreAddedOnceAndRemoved() = runBlocking {
        addSong("s1")
        addSong("s2")
        val id = repository.save(playlist("Mix"))

        assertTrue(repository.addSong(id, "s1"))
        assertFalse(repository.addSong(id, "s1"))
        assertTrue(repository.addSong(id, "s2"))
        repository.removeSong(id, "s1")

        assertEquals(listOf("s2"), repository.songsOf(id).asList().map { it.id })
    }

    @Test
    fun theSongsOfAnotherUsersPlaylistCannotChange() {
        val theirs = runBlocking {
            addSong("s1")
            othersPlaylist()
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addSong(theirs, "s1") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.removeSong(theirs, "s1") }
        }
    }

    @Test
    fun anotherUsersPlaylistIsNotDeleted() = runBlocking {
        val theirs = othersPlaylist()

        repository.delete(theirs)

        assertNotNull(database.userPlaylistDao().get(theirs))
    }

    @Test
    fun onlyTheUsersOwnPlaylistsAreListedAndSearched() = runBlocking {
        othersPlaylist()
        repository.save(playlist("Road trip"))
        repository.save(playlist("Gym"))

        assertEquals(listOf("Gym", "Road trip"), repository.all().asList().map { it.name })
        assertEquals(listOf("Gym", "Road trip"), repository.list().map { it.name })
        assertEquals(listOf("Road trip"), repository.search("trip").asList().map { it.name })
        assertEquals(emptyList<String>(), repository.search("Theirs").asList().map { it.name })
    }

    @Test
    fun playlistsAreFilteredByOrigin() = runBlocking {
        othersPlaylist()
        repository.save(playlist("Mine"))
        repository.save(UserPlaylist(origin = Origin.YOUTUBE, name = "Liked music"))

        val tube = setOf(Origin.YOUTUBE)
        assertEquals(listOf("Liked music"), repository.all(origins = tube).asList().map { it.name })
        assertEquals(listOf("Liked music"), repository.list(origins = tube).map { it.name })
        assertEquals(2, repository.count())
        assertEquals(1, repository.count(tube))
        assertEquals("Liked music", repository.random(tube)?.name)
        assertEquals(null, repository.random(setOf(Origin.OPEN_SUBSONIC)))
        assertEquals(setOf(Origin.LOCAL, Origin.YOUTUBE), repository.origins().first())
    }

    @Test
    fun theSongsOfAPlaylistAreSorted() = runBlocking {
        addAlbum("a1")
        addSong("third", albumId = "a1", albumPosition = 3)
        addSong("first", albumId = "a1", albumPosition = 1)
        addSong("unknown", albumId = "a1")
        val id = repository.save(playlist("Mix"))
        listOf("third", "first", "unknown").forEach { repository.addSong(id, it) }

        assertEquals(
            listOf("first", "third", "unknown"),
            repository.songsOf(id, SortOrder(SongSort.ALBUM_POSITION)).asList().map { it.id }
        )
    }
}
