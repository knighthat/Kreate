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

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate.database.DatabaseTest
import me.knighthat.kreate.database.asList
import me.knighthat.kreate.database.entity.UserAlbumEntity
import me.knighthat.kreate.database.entity.UserArtistEntity
import me.knighthat.kreate.database.entity.UserPlaylistEntity
import me.knighthat.kreate.database.entity.UserSongEntity
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

internal class RemoteCatalogRepositoryTest : DatabaseTest() {

    private val server = Origin.OPEN_SUBSONIC
    private val star = Instant.parse("2024-05-01T10:00:00Z")

    private lateinit var repository: RemoteCatalogRepositoryImpl

    private fun album(id: String, name: String? = id, year: Int? = null, origin: Origin = server) =
        Album(id, origin, name, year = year)

    private fun artist(id: String, name: String? = id) = Artist(id, server, name)

    private fun song(
        id: String,
        album: Album? = null,
        artists: List<Artist> = emptyList(),
        starredAt: Instant? = null,
    ) = RemoteSong(Song(id, server, id, albumId = album?.id), album, artists, starredAt)

    @Before
    fun setUp() = runBlocking {
        addUser()
        repository = RemoteCatalogRepositoryImpl(
            database,
            database.albumDao(),
            database.artistDao(),
            database.userAlbumDao(),
            database.userArtistDao(),
            database.userPlaylistDao(),
            database.songDao(),
            database.userSongDao(),
            activeUser,
            clock,
        )
    }

    //<editor-fold desc="Albums">
    @Test
    fun albumsAreStoredWithALibraryRowAndTheirStar() = runBlocking {
        repository.syncAlbums(
            server,
            listOf(RemoteItem(album("a1"), star), RemoteItem(album("a2"), null))
        )

        assertEquals("a1", database.albumDao().get("a1")?.name)
        assertEquals(star, database.userAlbumDao().get(USER, "a1")?.bookmarkedAt)
        assertNotNull(database.userAlbumDao().get(USER, "a2"))
        assertNull(database.userAlbumDao().get(USER, "a2")?.bookmarkedAt)
    }

    @Test
    fun aStarTakenAwayOnTheServerIsCleared() = runBlocking {
        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), star)))
        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null)))

        assertNull(database.userAlbumDao().get(USER, "a1")?.bookmarkedAt)
    }

    @Test
    fun aDislikeSurvivesASyncWithoutStar() = runBlocking {
        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null)))
        database.userAlbumDao().upsert(
            UserAlbumEntity(
                USER,
                "a1",
                bookmarkedAt = DISLIKED,
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null)))

        assertEquals(DISLIKED, database.userAlbumDao().get(USER, "a1")?.bookmarkedAt)
    }

    @Test
    fun aStarOnTheServerReplacesADislike() = runBlocking {
        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null)))
        database.userAlbumDao().upsert(
            UserAlbumEntity(
                USER,
                "a1",
                bookmarkedAt = DISLIKED,
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), star)))

        assertEquals(star, database.userAlbumDao().get(USER, "a1")?.bookmarkedAt)
    }

    @Test
    fun albumsTheServerDroppedLeaveTheLibrary() = runBlocking {
        addAlbum("local")
        database.userAlbumDao()
            .upsert(UserAlbumEntity(USER, "local", createdAt = EPOCH, updatedAt = EPOCH))
        repository.syncAlbums(
            server,
            listOf(RemoteItem(album("a1"), null), RemoteItem(album("a2"), null))
        )

        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null)))

        assertNotNull(database.userAlbumDao().get(USER, "a1"))
        assertNull(database.userAlbumDao().get(USER, "a2"))
        // Only the library of this server is brought in line
        assertNotNull(database.userAlbumDao().get(USER, "local"))
    }

    @Test
    fun anUnchangedAlbumIsNotWrittenAgain() = runBlocking {
        val created = clock.now()
        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), star)))
        clock.advance()

        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), star)))

        assertEquals(created, database.albumDao().get("a1")?.updatedAt)
        assertEquals(created, database.userAlbumDao().get(USER, "a1")?.updatedAt)
    }

    @Test
    fun aChangedAlbumKeepsWhenItWasFirstStored() = runBlocking {
        val created = clock.now()
        repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null)))
        clock.advance()

        repository.syncAlbums(server, listOf(RemoteItem(album("a1", name = "Renamed"), null)))

        val stored = database.albumDao().get("a1")!!
        assertEquals("Renamed", stored.name)
        assertEquals(created, stored.createdAt)
        assertEquals(clock.now(), stored.updatedAt)
    }

    @Test
    fun itemsOfAnotherOriginAreRefused() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.syncAlbums(
                    server,
                    listOf(RemoteItem(album("a1", origin = Origin.LOCAL), null))
                )
            }
        }
        assertNull(database.albumDao().get("a1"))
    }

    @Test
    fun aSyncWaitsForTheActiveUser() = runBlocking {
        activeUser.id.value = null

        val sync = async { repository.syncAlbums(server, listOf(RemoteItem(album("a1"), null))) }
        delay(200)
        assertFalse(sync.isCompleted)

        activeUser.id.value = USER
        sync.await()
        assertNotNull(database.userAlbumDao().get(USER, "a1"))
    }
    //</editor-fold>

    //<editor-fold desc="Artists">
    @Test
    fun artistsFollowTheStarsOfTheServer() = runBlocking {
        repository.syncArtists(
            server,
            listOf(
                RemoteItem(artist("r1"), star),
                RemoteItem(artist("r2"), star),
                RemoteItem(artist("r3"), null)
            )
        )
        database.userArtistDao().upsert(
            UserArtistEntity(
                USER,
                "r3",
                followSince = DISLIKED,
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        repository.syncArtists(
            server,
            listOf(
                RemoteItem(artist("r1"), star),
                RemoteItem(artist("r2"), null),
                RemoteItem(artist("r3"), null)
            )
        )

        assertEquals(star, database.userArtistDao().get(USER, "r1")?.followSince)
        assertNull(database.userArtistDao().get(USER, "r2")?.followSince)
        assertEquals(DISLIKED, database.userArtistDao().get(USER, "r3")?.followSince)
    }

    @Test
    fun artistsTheServerDroppedLeaveTheLibrary() = runBlocking {
        repository.syncArtists(
            server,
            listOf(RemoteItem(artist("r1"), null), RemoteItem(artist("r2"), null))
        )

        repository.syncArtists(server, listOf(RemoteItem(artist("r2"), null)))

        assertNull(database.userArtistDao().get(USER, "r1"))
        assertNotNull(database.userArtistDao().get(USER, "r2"))
    }
    //</editor-fold>

    //<editor-fold desc="Songs">
    @Test
    fun songsBringWhatTheyPointAt() = runBlocking {
        val album = album("a1")
        repository.syncSongs(
            server,
            listOf(song("s1", album, listOf(artist("r1"), artist("r2")), star))
        )

        assertEquals("a1", database.songDao().get("s1")?.albumId)
        assertNotNull(database.albumDao().get("a1"))
        assertEquals(
            setOf("r1", "r2"),
            database.songDao().artistsOf("s1").first().map { it.id }.toSet()
        )
        assertEquals(star, database.userSongDao().get(USER, "s1")?.likedAt)
    }

    @Test
    fun aSongDoesNotOverwriteWhatIsKnownAboutItsAlbum() = runBlocking {
        repository.syncAlbums(
            server,
            listOf(RemoteItem(album("a1", name = "Full", year = 2001), null))
        )

        // A song only knows part of its album
        repository.syncSongs(server, listOf(song("s1", album("a1", name = "Part"))))

        val stored = database.albumDao().get("a1")!!
        assertEquals("Full", stored.name)
        assertEquals(2001, stored.year)
    }

    @Test
    fun albumsAndArtistsSentAlongAreStoredInFull() = runBlocking {
        repository.syncAlbums(server, listOf(RemoteItem(album("a1", name = "Old"), null)))

        repository.syncSongs(
            server,
            listOf(song("s1", album("a1", name = "Part"))),
            albums = listOf(RemoteItem(album("a1", name = "New", year = 2020), star)),
            artists = listOf(RemoteItem(artist("r1"), star)),
        )

        assertEquals("New", database.albumDao().get("a1")?.name)
        assertEquals(star, database.userAlbumDao().get(USER, "a1")?.bookmarkedAt)
        assertEquals(star, database.userArtistDao().get(USER, "r1")?.followSince)
    }

    @Test
    fun aSongIsCreditedToExactlyTheArtistsTheServerNames() = runBlocking {
        repository.syncSongs(
            server,
            listOf(
                song("s1", artists = listOf(artist("x"), artist("y"))),
                song("s2", artists = listOf(artist("x")))
            )
        )

        repository.syncSongs(server, listOf(song("s1", artists = listOf(artist("y"), artist("z")))))

        assertEquals(
            setOf("y", "z"),
            database.songDao().artistsOf("s1").first().map { it.id }.toSet()
        )
        // Songs that weren't part of the sync keep their artists
        assertEquals(listOf("x"), database.songDao().artistsOf("s2").first().map { it.id })
    }

    @Test
    fun aSongTheServerHasIsAvailableAgain() = runBlocking {
        addSong("s1", origin = server, unavailableSince = EPOCH)

        repository.syncSongs(server, listOf(song("s1")))

        assertNull(database.songDao().get("s1")?.unavailableSince)
    }

    @Test
    fun aSongsLikeFollowsTheStarAndKeepsADislike() = runBlocking {
        repository.syncSongs(
            server,
            listOf(
                song("liked", starredAt = star),
                song("disliked"),
                song("unstarred", starredAt = star)
            )
        )
        database.userSongDao().upsert(
            UserSongEntity(
                USER,
                "disliked",
                likedAt = DISLIKED,
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        repository.syncSongs(
            server,
            listOf(song("liked", starredAt = star), song("disliked"), song("unstarred"))
        )

        assertEquals(star, database.userSongDao().get(USER, "liked")?.likedAt)
        assertEquals(DISLIKED, database.userSongDao().get(USER, "disliked")?.likedAt)
        assertNull(database.userSongDao().get(USER, "unstarred")?.likedAt)
    }

    @Test
    fun theSongsOfAPlaylistAreReplaced() = runBlocking {
        repository.syncPlaylists(
            server,
            listOf(UserPlaylist(origin = server, name = "Mix", remoteId = "p1"))
        )
        val playlistId =
            database.userPlaylistDao().withRemoteOrigin(USER, server).single().playlistId
        repository.syncSongs(server, listOf(song("s1"), song("s2")), playlistId = playlistId)

        repository.syncSongs(server, listOf(song("s2"), song("s3")), playlistId = playlistId)

        val songs = UserSongRepositoryImpl(database, database.userSongDao(), activeUser, clock)
        assertEquals(
            listOf("s2", "s3"),
            songs.ofPlaylist(playlistId).asList().map { it.songId }
        )
    }

    @Test
    fun theSongsOfAnotherUsersPlaylistAreNotReplaced() = runBlocking {
        addUser("other")
        val playlistId = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = "other",
                origin = server,
                name = "Theirs",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.syncSongs(
                    server,
                    listOf(song("s1")),
                    playlistId = playlistId
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.syncSongs(server, listOf(song("s1")), playlistId = 999) }
        }
        // All or nothing, the songs aren't stored either
        assertNull(database.songDao().get("s1"))
    }
    //</editor-fold>

    //<editor-fold desc="Playlists">
    @Test
    fun playlistsFollowTheServer() = runBlocking {
        repository.syncPlaylists(
            server,
            listOf(
                UserPlaylist(origin = server, name = "One", remoteId = "p1"),
                UserPlaylist(origin = server, name = "Two", remoteId = "p2"),
            )
        )
        val firstId = database.userPlaylistDao().withRemoteOrigin(USER, server)
            .single { it.remoteId == "p1" }.playlistId
        clock.advance()

        repository.syncPlaylists(
            server,
            listOf(UserPlaylist(origin = server, name = "Renamed", remoteId = "p1"))
        )

        val stored = database.userPlaylistDao().withRemoteOrigin(USER, server).single()
        assertEquals("p1", stored.remoteId)
        // The row is kept, so its songs and id stay
        assertEquals(firstId, stored.playlistId)
        assertEquals("Renamed", stored.name)
        assertEquals(clock.now(), stored.updatedAt)
    }

    @Test
    fun ownPlaylistsAreNotTouchedByASync() = runBlocking {
        val own = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "Mine",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )

        repository.syncPlaylists(server, emptyList())

        assertNotNull(database.userPlaylistDao().get(own))
    }

    @Test
    fun aPlaylistWithoutRemoteIdIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.syncPlaylists(
                    server,
                    listOf(UserPlaylist(origin = server, name = "No id"))
                )
            }
        }
    }

    @Test
    fun anUnchangedPlaylistIsNotWrittenAgain() = runBlocking {
        val created = clock.now()
        repository.syncPlaylists(
            server,
            listOf(UserPlaylist(origin = server, name = "One", remoteId = "p1"))
        )
        clock.advance()

        repository.syncPlaylists(
            server,
            listOf(UserPlaylist(origin = server, name = "One", remoteId = "p1"))
        )

        assertTrue(
            database.userPlaylistDao().withRemoteOrigin(USER, server)
                .all { it.updatedAt == created })
    }
    //</editor-fold>
}
