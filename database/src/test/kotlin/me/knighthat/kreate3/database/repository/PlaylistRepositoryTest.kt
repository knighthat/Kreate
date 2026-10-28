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

import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.DatabaseTest
import me.knighthat.kreate3.database.asList
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.paging.SongSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.moveSong(theirs, "s1", null) }
        }
    }

    private suspend fun orderOf(playlistId: Long) =
        database.userPlaylistDao().songLinks(playlistId).map { it.songId }

    @Test
    fun songsAreAddedAtTheEnd() = runBlocking {
        listOf("b", "c", "a").forEach { addSong(it) }
        val id = repository.save(playlist("Mix"))

        listOf("b", "c", "a").forEach { repository.addSong(id, it) }
        repository.addSong(id, "b")

        assertEquals(listOf("b", "c", "a"), orderOf(id))
    }

    @Test
    fun aSongIsMovedAfterAnotherOrToTheStart() = runBlocking {
        listOf("s1", "s2", "s3", "s4").forEach { addSong(it) }
        val id = repository.save(playlist("Mix"))
        listOf("s1", "s2", "s3", "s4").forEach { repository.addSong(id, it) }

        repository.moveSong(id, "s1", after = "s3")
        assertEquals(listOf("s2", "s3", "s1", "s4"), orderOf(id))

        repository.moveSong(id, "s4", after = null)
        assertEquals(listOf("s4", "s2", "s3", "s1"), orderOf(id))

        repository.moveSong(id, "s2", after = "s1")
        assertEquals(listOf("s4", "s3", "s1", "s2"), orderOf(id))
    }

    @Test
    fun movingAfterARemovalKeepsTheOrder() = runBlocking {
        listOf("s1", "s2", "s3", "s4", "s5").forEach { addSong(it) }
        val id = repository.save(playlist("Mix"))
        listOf("s1", "s2", "s3", "s4", "s5").forEach { repository.addSong(id, it) }
        repository.removeSong(id, "s2")
        repository.removeSong(id, "s3")

        repository.moveSong(id, "s5", after = "s1")
        repository.addSong(id, "s2")

        assertEquals(listOf("s1", "s5", "s4", "s2"), orderOf(id))
    }

    @Test
    fun aRemovedSongGoesBackToItsPlace() = runBlocking {
        listOf("s1", "s2", "s3").forEach { addSong(it) }
        val id = repository.save(playlist("Mix"))
        listOf("s1", "s2", "s3").forEach { repository.addSong(id, it) }

        val position = repository.removeSong(id, "s2")
        assertEquals(1, position)
        assertNull(repository.removeSong(id, "s2"))
        repository.addSong(id, "s2", position)
        assertEquals(listOf("s1", "s2", "s3"), orderOf(id))

        // Moving numbers the songs again, the place it left is taken by then
        repository.removeSong(id, "s1")
        repository.moveSong(id, "s3", after = null)
        repository.addSong(id, "s1", 0)
        assertEquals(listOf("s1", "s3", "s2"), orderOf(id))
    }

    @Test
    fun aSongOutsideThePlaylistIsNotMoved() {
        val id = runBlocking {
            listOf("s1", "s2").forEach { addSong(it) }
            repository.save(playlist("Mix")).also { repository.addSong(it, "s1") }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.moveSong(id, "s2", after = null) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.moveSong(id, "s1", after = "s2") }
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

    /** A song in the user's library whose cover is [cover]. */
    private suspend fun librarySong(id: String, cover: String?, likedAt: Instant? = null) {
        addSong(id)
        database.userSongDao().upsert(
            UserSongEntity(
                userId = USER,
                songId = id,
                thumbnail = cover?.let(Uri::parse),
                likedAt = likedAt,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
    }

    private suspend fun play(songId: String, times: Int, userId: String = USER) = repeat(times) {
        database.playbackHistoryDao().insert(
            PlaybackHistoryEntity(
                songId = songId,
                userId = userId,
                timeSpent = 10.seconds,
                createdAt = EPOCH,
            )
        )
    }

    private suspend fun coversOf(name: String): List<Pair<String, String>> =
        repository.all().asList().single { it.name == name }.covers
            .map { it.songId to it.thumbnail.toString() }

    @Test
    fun coversAreOfTheMostPlayedSongsOneSongPerCover() = runBlocking {
        librarySong("a", "x")
        librarySong("b", "y")
        librarySong("c", "x")
        librarySong("d", "z")
        librarySong("e", "w")
        librarySong("f", null)
        librarySong("g", "v")
        val id = repository.save(playlist("Mix"))
        listOf("a", "b", "c", "d", "e", "f", "g").forEach { repository.addSong(id, it) }
        play("a", 1)
        play("b", 3)
        play("c", 5)
        play("e", 2)
        play("f", 9)

        // c wins x over a, f has no cover, d comes before g in the playlist
        assertEquals(
            listOf("c" to "x", "b" to "y", "e" to "w", "d" to "z"),
            coversOf("Mix"),
        )
    }

    @Test
    fun coversLeaveOutHiddenSongsAndThePlaysOfOthers() = runBlocking {
        librarySong("kept", "x")
        librarySong("disliked", "y", likedAt = DISLIKED)
        librarySong("liked", "z", likedAt = EPOCH)
        addSong("notInLibrary")
        val id = repository.save(playlist("Mix"))
        listOf("kept", "disliked", "liked", "notInLibrary").forEach { repository.addSong(id, it) }
        play("disliked", 9)
        play("liked", 5, userId = "other")
        play("kept", 1)

        assertEquals(listOf("kept" to "x", "liked" to "z"), coversOf("Mix"))
    }

    @Test
    fun anEmptyPlaylistHasNoCovers() = runBlocking {
        repository.save(playlist("Empty"))

        assertEquals(emptyList<Pair<String, String>>(), coversOf("Empty"))
    }

    @Test
    fun songsPlayedAsOftenKeepThePlaylistOrder() = runBlocking {
        librarySong("a", "x")
        librarySong("b", "y")
        val id = repository.save(playlist("Mix"))
        listOf("b", "a").forEach { repository.addSong(id, it) }
        assertEquals(listOf("b" to "y", "a" to "x"), coversOf("Mix"))

        play("a", 1)

        assertEquals(listOf("a" to "x", "b" to "y"), coversOf("Mix"))
    }
}
