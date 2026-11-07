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
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal class RemoteCatalogRepositoryTest : DatabaseTest() {

    private val server = Origin.OPEN_SUBSONIC
    private val star = Instant.parse("2024-05-01T10:00:00Z")

    private lateinit var repository: RemoteCatalogRepositoryImpl

    private fun album(id: String, name: String? = id, origin: Origin = server) =
        Album(id, origin, name)

    private fun artist(id: String, name: String? = id) = Artist(id, server, name)

    private fun song(
        id: String,
        album: Album? = null,
        artists: List<Artist> = emptyList(),
        starredAt: Instant? = null,
        origin: Origin = server,
    ) = RemoteSong(Song(id, origin, id, albumId = album?.id), album, artists, starredAt)

    private suspend fun likedAt(songId: String) = database.userSongDao().get(USER, songId)?.likedAt

    private suspend fun dislike(songId: String) = database.userSongDao().upsert(
        UserSongEntity(USER, songId, likedAt = DISLIKED, createdAt = EPOCH, updatedAt = EPOCH)
    )

    /** Puts songs in the user's library, not liked, sync only reaches those. */
    private suspend fun stored(vararg ids: String, origin: Origin = server) {
        for (id in ids) {
            addSong(id, origin)
            database.userSongDao()
                .upsert(UserSongEntity(USER, id, createdAt = EPOCH, updatedAt = EPOCH))
        }
    }

    @Before
    fun setUp() = runBlocking {
        addUser()
        repository = RemoteCatalogRepositoryImpl(
            database,
            database.albumDao(),
            database.artistDao(),
            database.songDao(),
            database.userSongDao(),
            activeUser,
            clock,
        )
    }

    @Test
    fun likedSongsBringWhatTheyPointAt() = runBlocking {
        stored("s1")
        repository.syncLikedSongs(
            server,
            listOf(song("s1", album("a1"), listOf(artist("r1"), artist("r2")), star))
        )

        assertEquals("a1", database.songDao().get("s1")?.albumId)
        assertNotNull(database.albumDao().get("a1"))
        assertEquals(
            setOf("r1", "r2"),
            database.songDao().artistsOf("s1").first().map { it.id }.toSet()
        )
        assertEquals(star, likedAt("s1"))
        // What a song points at is only in the catalog, not in the user's library
        assertNull(database.userAlbumDao().get(USER, "a1"))
        assertNull(database.userArtistDao().get(USER, "r1"))
    }

    @Test
    fun aSongDoesNotOverwriteWhatIsKnownAboutItsAlbum() = runBlocking {
        stored("s1")
        addAlbum("a1", origin = server, name = "Full", year = 2001)

        // A song only knows part of its album
        repository.syncLikedSongs(server, listOf(song("s1", album("a1", name = "Part"))))

        val stored = database.albumDao().get("a1")!!
        assertEquals("Full", stored.name)
        assertEquals(2001, stored.year)
    }

    @Test
    fun theServersValuesOfASongReplaceTheStoredOnes() = runBlocking {
        stored("s1")
        database.songDao().upsert(database.songDao().get("s1")!!.copy(name = "Old"))

        repository.syncLikedSongs(server, listOf(song("s1")))

        assertEquals("s1", database.songDao().get("s1")?.name)
    }

    @Test
    fun aSongIsCreditedToExactlyTheArtistsTheServerNames() = runBlocking {
        stored("s1", "s2")
        repository.syncLikedSongs(
            server,
            listOf(
                song("s1", artists = listOf(artist("x"), artist("y"))),
                song("s2", artists = listOf(artist("x")))
            )
        )

        repository.syncLikedSongs(
            server,
            listOf(song("s1", artists = listOf(artist("y"), artist("z")))),
            complete = false,
        )

        assertEquals(
            setOf("y", "z"),
            database.songDao().artistsOf("s1").first().map { it.id }.toSet()
        )
        // Songs that weren't part of the sync keep their artists
        assertEquals(listOf("x"), database.songDao().artistsOf("s2").first().map { it.id })
    }

    @Test
    fun aSongTheServerHasIsAvailableAgain() = runBlocking {
        stored("s1")
        database.songDao().upsert(database.songDao().get("s1")!!.copy(unavailableSince = EPOCH))

        repository.syncLikedSongs(server, listOf(song("s1")))

        assertNull(database.songDao().get("s1")?.unavailableSince)
    }

    @Test
    fun likedSongsAreLikedAtTheirTimeOrNowInTheServersOrder() = runBlocking {
        stored("newest", "dated", "oldest")
        repository.syncLikedSongs(
            server,
            listOf(song("newest"), song("dated", starredAt = star), song("oldest"))
        )

        assertEquals(clock.time, likedAt("newest"))
        assertEquals(star, likedAt("dated"))
        assertEquals(clock.time - 2.milliseconds, likedAt("oldest"))
    }

    @Test
    fun theServersLikeTimeReplacesTheStoredOne() = runBlocking {
        stored("s1")
        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star)))

        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star + 1.hours)))

        assertEquals(star + 1.hours, likedAt("s1"))
    }

    @Test
    fun aLikeAlreadyStoredKeepsItsTimeWhenTheServerGivesNone() = runBlocking {
        stored("s1")
        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star)))
        clock.advance(1.hours)

        repository.syncLikedSongs(server, listOf(song("s1")))

        assertEquals(star, likedAt("s1"))
    }

    @Test
    fun aLikeTheServerNoLongerHasIsTakenAwayButTheSongStays() = runBlocking {
        stored("kept", "gone")
        repository.syncLikedSongs(
            server,
            listOf(song("kept", starredAt = star), song("gone", starredAt = star))
        )

        repository.syncLikedSongs(server, listOf(song("kept", starredAt = star)))

        assertEquals(star, likedAt("kept"))
        assertNotNull(database.userSongDao().get(USER, "gone"))
        assertNull(likedAt("gone"))
        assertNotNull(database.songDao().get("gone"))
    }

    @Test
    fun anIncompleteListTakesNoLikeAway() = runBlocking {
        stored("first", "second")
        repository.syncLikedSongs(
            server,
            listOf(song("first", starredAt = star), song("second", starredAt = star))
        )

        repository.syncLikedSongs(server, listOf(song("first", starredAt = star)), complete = false)

        assertEquals(star, likedAt("second"))
    }

    @Test
    fun aLikeReplacesADislikeAndOtherDislikesStay() = runBlocking {
        stored("liked", "disliked")
        dislike("liked")
        dislike("disliked")
        clock.advance(1.hours)

        repository.syncLikedSongs(server, listOf(song("liked")))

        // Liked again now, the dislike's time isn't a like time to keep
        assertEquals(clock.time, likedAt("liked"))
        assertEquals(DISLIKED, likedAt("disliked"))
    }

    @Test
    fun likesOfOtherOriginsStay() = runBlocking {
        stored("yt", origin = Origin.YOUTUBE)
        repository.syncLikedSongs(
            Origin.YOUTUBE,
            listOf(song("yt", starredAt = star, origin = Origin.YOUTUBE))
        )

        repository.syncLikedSongs(server, emptyList())

        assertEquals(star, likedAt("yt"))
    }

    @Test
    fun anUnchangedSongIsNotWrittenAgain() = runBlocking {
        stored("s1")
        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star)))
        val songStamp = database.songDao().get("s1")?.updatedAt
        val userStamp = database.userSongDao().get(USER, "s1")?.updatedAt
        clock.advance()

        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star)))

        assertEquals(songStamp, database.songDao().get("s1")?.updatedAt)
        assertEquals(userStamp, database.userSongDao().get(USER, "s1")?.updatedAt)
    }

    @Test
    fun itemsOfAnotherOriginAreRefused() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.syncLikedSongs(
                    server,
                    listOf(song("s1", album("a1", origin = Origin.LOCAL)))
                )
            }
        }
        assertNull(database.songDao().get("s1"))
    }

    @Test
    fun aSyncWaitsForTheActiveUser() = runBlocking {
        activeUser.id.value = null

        stored("s1")
        val sync = async { repository.syncLikedSongs(server, listOf(song("s1"))) }
        delay(200)
        assertFalse(sync.isCompleted)

        activeUser.id.value = USER
        sync.await()
        assertNotNull(likedAt("s1"))
    }

    @Test
    fun songsTheLibraryDoesNotHaveAreNotAdded() = runBlocking {
        stored("kept")

        repository.syncLikedSongs(server, listOf(song("kept"), song("new", album("a1"))))

        assertNotNull(likedAt("kept"))
        assertNull(database.userSongDao().get(USER, "new"))
        assertNull(database.songDao().get("new"))
        assertNull(database.albumDao().get("a1"))
    }

    @Test
    fun aPlayedSongJoinsTheLibraryWithoutALike() = runBlocking {
        repository.savePlayedSong(song("s1", album("a1"), listOf(artist("r1"))))

        val row = database.userSongDao().get(USER, "s1")
        assertNotNull(row)
        assertNull(row!!.likedAt)
        assertEquals("a1", database.songDao().get("s1")?.albumId)
        assertEquals(listOf("r1"), database.songDao().artistsOf("s1").first().map { it.id })
    }

    @Test
    fun playingASongOfTheLibraryKeepsItsRowButTakesTheServersValues() = runBlocking {
        stored("s1", "s2")
        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star)))
        dislike("s2")

        repository.savePlayedSong(song("s1", artists = listOf(artist("r1"))))
        repository.savePlayedSong(song("s2"))

        assertEquals(star, likedAt("s1"))
        assertEquals(DISLIKED, likedAt("s2"))
        assertEquals(listOf("r1"), database.songDao().artistsOf("s1").first().map { it.id })
    }

    @Test
    fun aPlayedSongOfTheDeviceIsRefused() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.savePlayedSong(song("s1", origin = Origin.LOCAL)) }
        }
        assertNull(database.songDao().get("s1"))
    }

    @Test
    fun aQueuedSongIsOnlyInTheCatalogUntilItJoinsTheLibrary() = runBlocking {
        repository.saveQueuedSong(song("s1", album("a1"), listOf(artist("r1"))))

        assertEquals("a1", database.songDao().get("s1")?.albumId)
        assertNull(database.userSongDao().get(USER, "s1"))

        assertEquals(true, repository.addToLibrary("s1"))
        assertNotNull(database.userSongDao().get(USER, "s1"))
        assertNull(database.userSongDao().get(USER, "s1")?.likedAt)
    }

    @Test
    fun addingToTheLibraryKeepsARowAndNeedsTheCatalog() = runBlocking {
        stored("s1")
        repository.syncLikedSongs(server, listOf(song("s1", starredAt = star)))

        assertEquals(true, repository.addToLibrary("s1"))
        assertEquals(star, likedAt("s1"))
        assertEquals(false, repository.addToLibrary("missing"))
        assertNull(database.userSongDao().get(USER, "missing"))
    }

    @Test
    fun aQueuedSongOfTheDeviceIsRefused() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveQueuedSong(song("s1", origin = Origin.LOCAL)) }
        }
        assertNull(database.songDao().get("s1"))
    }
}
