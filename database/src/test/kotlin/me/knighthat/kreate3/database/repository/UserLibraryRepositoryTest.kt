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
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.model.UserAlbum
import me.knighthat.kreate3.database.model.UserArtist
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserSongSort
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The user's songs, albums and artists, and the views that combine them with the catalog.
 */
internal class UserLibraryRepositoryTest : DatabaseTest() {

    private val liked = Instant.fromEpochMilliseconds(5_000)

    private lateinit var songs: UserSongRepositoryImpl
    private lateinit var albums: UserAlbumRepositoryImpl
    private lateinit var artists: UserArtistRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        addUser()
        songs = UserSongRepositoryImpl(database, database.userSongDao(), activeUser, clock)
        albums = UserAlbumRepositoryImpl(database, database.userAlbumDao(), activeUser, clock)
        artists = UserArtistRepositoryImpl(database, database.userArtistDao(), activeUser, clock)
    }

    private suspend fun librarySong(id: String, likedAt: Instant? = null, name: String? = id) =
        songs.save(UserSong(id, likedAt = likedAt, name = name.takeIf { it != id }))

    //<editor-fold desc="Songs">
    @Test
    fun forgetThumbnailDeletesRowsKeepingNothingElseAndClearsTheRest() = runBlocking {
        val file = Uri.parse("file:///data/download_covers/a-1440.jpg")
        val other = Uri.parse("file:///data/download_covers/b-1440.jpg")
        addUser("other")
        addSong("s1")
        addSong("s2")
        // Only the cover: the row goes
        songs.save(UserSong("s1", thumbnail = file))
        songs.save(UserSong("s2", thumbnail = other))
        activeUser.id.value = "other"
        // Liked too: the row stays with the shared cover
        songs.save(UserSong("s1", thumbnail = file, likedAt = liked))

        songs.forgetThumbnail(file)

        assertNull(database.userSongDao().get(USER, "s1"))
        assertEquals(liked, database.userSongDao().get("other", "s1")?.likedAt)
        assertNull(database.userSongDao().get("other", "s1")?.thumbnail)
        // Another cover stays
        assertEquals(other, database.userSongDao().get(USER, "s2")?.thumbnail)
        assertEquals(listOf(other), songs.fileThumbnails())
    }

    @Test
    fun eachUserDownloadsOnTheirOwn() = runBlocking {
        val cover = Uri.parse("file:///data/download_covers/a-1440.jpg")
        addUser("other")
        addSong("s1")
        songs.save(UserSong("s1", thumbnail = cover, downloadedAt = liked))
        activeUser.id.value = "other"
        songs.save(UserSong("s1", likedAt = liked, downloadedAt = liked))

        assertEquals(setOf("s1"), songs.downloadedIds().first())
        assertEquals(setOf("s1"), songs.downloadedByAnyone())

        // The other user leaves: they keep the like, the download goes
        songs.forgetDownload("s1", cover)
        assertEquals(emptySet<String>(), songs.downloadedIds().first())
        assertEquals(liked, database.userSongDao().get("other", "s1")?.likedAt)
        assertEquals(setOf("s1"), songs.downloadedByAnyone())

        // The last one leaves: a row keeping only the download and its cover goes
        activeUser.id.value = USER
        songs.forgetDownload("s1", cover)
        assertNull(database.userSongDao().get(USER, "s1"))
        assertEquals(emptySet<String>(), songs.downloadedByAnyone())
    }

    @Test
    fun forgetDownloadOfEveryUserAndDownloadersUpdate() = runBlocking {
        val cover = Uri.parse("file:///data/download_covers/a-1440.jpg")
        addUser("other")
        addSong("s1")
        songs.save(UserSong("s1", name = "Mine", downloadedAt = liked))
        activeUser.id.value = "other"
        songs.save(UserSong("s1", downloadedAt = liked))

        songs.updateDownloaders("s1") { it.copy(thumbnail = cover) }
        assertEquals(cover, database.userSongDao().get(USER, "s1")?.thumbnail)
        assertEquals(cover, database.userSongDao().get("other", "s1")?.thumbnail)

        songs.forgetDownload("s1", cover, everyUser = true)
        assertNull(database.userSongDao().get("other", "s1"))
        val kept = database.userSongDao().get(USER, "s1")
        assertEquals("Mine", kept?.name)
        assertNull(kept?.thumbnail)
        assertNull(kept?.downloadedAt)
    }

    @Test
    fun forgetThumbnailOfOneSongLeavesTheOthers() = runBlocking {
        val file = Uri.parse("file:///data/download_covers/a-1440.jpg")
        addSong("s1")
        addSong("s2")
        songs.save(UserSong("s1", thumbnail = file))
        songs.save(UserSong("s2", thumbnail = file, name = "Mine"))

        songs.forgetThumbnail(file, "s2")

        assertEquals(file, database.userSongDao().get(USER, "s1")?.thumbnail)
        assertNull(database.userSongDao().get(USER, "s2")?.thumbnail)
        assertEquals("Mine", database.userSongDao().get(USER, "s2")?.name)
    }

    @Test
    fun likedSongsLeaveOutDislikesAndNeutralOnes() = runBlocking {
        listOf("liked", "disliked", "neutral").forEach { addSong(it) }
        librarySong("liked", liked)
        librarySong("disliked", DISLIKED)
        librarySong("neutral")

        assertEquals(listOf("liked"), songs.liked().asList().map { it.songId })
        assertEquals(listOf("liked", "neutral"), songs.all().asList().map { it.songId })
    }

    @Test
    fun dislikedSongsAreLeftOutOfEveryListButStillFound() = runBlocking {
        addAlbum("a1")
        addArtist("r1")
        addSong("kept", albumId = "a1", artists = listOf("r1"))
        addSong("disliked", albumId = "a1", artists = listOf("r1"))
        librarySong("kept")
        librarySong("disliked", DISLIKED)
        val playlistId = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "Mix",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )
        listOf("kept", "disliked").forEachIndexed { position, id ->
            database.userPlaylistDao().addSong(SongPlaylistMapEntity(id, playlistId, position))
        }

        val kept = listOf("kept")
        assertEquals(kept, songs.all().asList().map { it.songId })
        assertEquals(kept, songs.list().map { it.songId })
        assertEquals(kept, songs.search("e").asList().map { it.songId })
        assertEquals(kept, songs.ofAlbum("a1").asList().map { it.songId })
        assertEquals(kept, songs.ofArtist("r1").asList().map { it.songId })
        assertEquals(kept, songs.ofPlaylist(playlistId).asList().map { it.songId })
        assertEquals(kept, songs.listOfAlbum("a1").map { it.songId })
        assertEquals(kept, songs.listOfArtist("r1").map { it.songId })
        assertEquals(kept, songs.listOfPlaylist(playlistId).map { it.songId })
        // Found to take the dislike back
        assertEquals(DISLIKED, songs.find("disliked").first()?.likedAt)
        assertEquals(setOf("disliked"), songs.dislikedIds().first())

        songs.update("kept") { it.copy(likedAt = DISLIKED) }
        assertEquals(setOf("kept", "disliked"), songs.listDislikedIds())
        assertTrue(songs.all().asList().isEmpty())
    }

    @Test
    fun dislikedSongsComeInTheOrderTheyWereDisliked() = runBlocking {
        addSong("old", origin = Origin.LOCAL)
        addSong("new", origin = Origin.YOUTUBE)
        addSong("liked", origin = Origin.OPEN_SUBSONIC)
        librarySong("liked", likedAt = Instant.fromEpochMilliseconds(5_000))
        // A dislike is the time it was made, made negative
        librarySong("old", likedAt = Instant.fromEpochMilliseconds(-2_000))
        librarySong("new", likedAt = Instant.fromEpochMilliseconds(-3_000))

        assertEquals(
            listOf("new", "old"),
            songs.disliked(emptySet(), newestFirst = true).asList().map { it.song.songId },
        )
        val oldestFirst = songs.disliked(emptySet(), newestFirst = false).asList()
        assertEquals(listOf("old", "new"), oldestFirst.map { it.song.songId })
        assertEquals(Instant.fromEpochMilliseconds(3_000), oldestFirst.last().dislikedAt)
        assertEquals(
            listOf("new"),
            songs.disliked(setOf(Origin.YOUTUBE), newestFirst = true).asList()
                .map { it.song.songId },
        )
        assertEquals(listOf("old"), songs.listDisliked(setOf(Origin.LOCAL)).map { it.songId })
        assertEquals(setOf("old", "new"), songs.listDislikedIds())
        assertEquals(listOf("liked"), songs.liked().asList().map { it.songId })
        // The liked song's origin has no dislike
        assertEquals(setOf(Origin.LOCAL, Origin.YOUTUBE), songs.dislikedOrigins().first())
    }

    @Test
    fun theOldMinusOneIsStillADislike() = runBlocking {
        addSong("s1")
        librarySong("s1", DISLIKED)

        assertEquals(setOf("s1"), songs.listDislikedIds())
        assertTrue(songs.all().asList().isEmpty())
        assertEquals(
            listOf("s1"),
            songs.disliked(emptySet(), newestFirst = true).asList().map { it.song.songId },
        )
    }

    @Test
    fun songsAreFilteredByOrigin() = runBlocking {
        addSong("local", origin = Origin.LOCAL)
        addSong("tube", origin = Origin.YOUTUBE)
        addSong("server", origin = Origin.OPEN_SUBSONIC)
        listOf("local", "tube", "server").forEach { librarySong(it) }

        val remote = setOf(Origin.YOUTUBE, Origin.OPEN_SUBSONIC)
        assertEquals(
            listOf("server", "tube"),
            songs.all(origins = remote).asList().map { it.songId })
        assertEquals(listOf("server", "tube"), songs.list(origins = remote).map { it.songId })
        assertEquals(listOf("local", "server", "tube"), songs.list().map { it.songId })
        assertEquals(Origin.entries.toSet(), songs.origins().first())
    }

    @Test
    fun anUnavailableSongIsHidden() = runBlocking {
        addSong("here")
        addSong("gone", unavailableSince = EPOCH)
        librarySong("here")
        librarySong("gone")

        assertEquals(listOf("here"), songs.all().asList().map { it.songId })
        assertNull(songs.find("gone").first())
    }

    @Test
    fun searchLooksAtTheNameArtistsAndAlbum() = runBlocking {
        addArtist("r1", name = "Queen")
        addAlbum("a1", name = "Night at the Opera")
        addSong("byName", name = "Bohemian")
        addSong("byArtist", name = "Other", artists = listOf("r1"))
        addSong("byAlbum", name = "Third", albumId = "a1")
        addSong("none", name = "Nothing")
        listOf("byName", "byArtist", "byAlbum", "none").forEach { librarySong(it) }

        assertEquals(listOf("byName"), songs.search("bohem").asList().map { it.songId })
        assertEquals(listOf("byArtist"), songs.search("QUEEN").asList().map { it.songId })
        assertEquals(listOf("byAlbum"), songs.search(" opera ").asList().map { it.songId })
    }

    @Test
    fun searchTakesWildcardsLiterally() = runBlocking {
        addSong("percent", name = "100% Pure")
        addSong("plain", name = "1000 Pure")
        addSong("underscore", name = "a_b")
        addSong("letter", name = "axb")
        listOf("percent", "plain", "underscore", "letter").forEach { librarySong(it) }

        assertEquals(listOf("percent"), songs.search("100%").asList().map { it.songId })
        assertEquals(listOf("underscore"), songs.search("a_b").asList().map { it.songId })
    }

    @Test
    fun songsOfAnAlbumArtistAndPlaylist() = runBlocking {
        addAlbum("a1")
        addArtist("r1")
        addSong("onAlbum", albumId = "a1")
        addSong("byArtist", artists = listOf("r1"))
        addSong("inPlaylist")
        listOf("onAlbum", "byArtist", "inPlaylist").forEach { librarySong(it) }
        val playlistId = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "Mix",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )
        database.userPlaylistDao().addSong(SongPlaylistMapEntity("inPlaylist", playlistId, 0))

        assertEquals(listOf("onAlbum"), songs.ofAlbum("a1").asList().map { it.songId })
        assertEquals(listOf("byArtist"), songs.ofArtist("r1").asList().map { it.songId })
        assertEquals(listOf("inPlaylist"), songs.ofPlaylist(playlistId).asList().map { it.songId })
        assertEquals(listOf("onAlbum"), songs.listOfAlbum("a1").map { it.songId })
        assertEquals(listOf("byArtist"), songs.listOfArtist("r1").map { it.songId })
        assertEquals(listOf("inPlaylist"), songs.listOfPlaylist(playlistId).map { it.songId })
    }

    @Test
    fun songsOfAPlaylistComeInItsOrder() = runBlocking {
        listOf("a", "b", "c").forEach {
            addSong(it)
            librarySong(it)
        }
        val playlistId = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "Mix",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )
        database.userPlaylistDao().addSong(SongPlaylistMapEntity("b", playlistId, 7))
        database.userPlaylistDao().addSong(SongPlaylistMapEntity("c", playlistId, 2))
        database.userPlaylistDao().addSong(SongPlaylistMapEntity("a", playlistId, 9))

        assertEquals(listOf("c", "b", "a"), songs.ofPlaylist(playlistId).asList().map { it.songId })
        assertEquals(listOf("c", "b", "a"), songs.listOfPlaylist(playlistId).map { it.songId })
        assertEquals(
            listOf("a", "b", "c"),
            songs.listOfPlaylist(playlistId, SortOrder(UserSongSort.NAME)).map { it.songId },
        )
    }

    @Test
    fun songsOfAnAlbumMoveInTheUsersOrder() = runBlocking {
        addAlbum("a1")
        listOf("a", "b", "c", "d").forEachIndexed { index, id ->
            addSong(id, albumId = "a1", albumPosition = index + 1)
        }
        // "d" isn't in the library yet, it neither shows nor counts in the order
        listOf("a", "b", "c").forEach { librarySong(it) }

        songs.moveInAlbum("a1", "c", after = null)
        assertEquals(listOf("c", "a", "b"), songs.listOfAlbum("a1").map { it.songId })
        songs.moveInAlbum("a1", "c", after = "b")
        assertEquals(listOf("a", "b", "c"), songs.ofAlbum("a1").asList().map { it.songId })
        songs.moveInAlbum("a1", "a", after = "c")
        assertEquals(
            listOf(2, 0, 1),
            listOf("a", "b", "c").map { database.userSongDao().get(USER, it)?.albumPosition },
        )

        // A scan numbering the album again doesn't change what the user picked
        addSong("a", albumId = "a1", albumPosition = 9)
        addSong("c", albumId = "a1", albumPosition = 0)
        librarySong("d")
        assertEquals(listOf("b", "c", "a", "d"), songs.listOfAlbum("a1").map { it.songId })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { songs.moveInAlbum("a1", "e", after = null) }
        }

        songs.resetAlbumOrder("a1")
        assertNull(database.userSongDao().get(USER, "a")?.albumPosition)
        assertEquals(listOf("c", "b", "d", "a"), songs.listOfAlbum("a1").map { it.songId })
        Unit
    }

    @Test
    fun aDislikedSongKeepsItsPlaceWhenTheAlbumIsReordered() = runBlocking {
        addAlbum("a1")
        listOf("a", "b", "c").forEachIndexed { position, id ->
            addSong(id, albumId = "a1", albumPosition = position)
        }
        librarySong("a")
        librarySong("b", DISLIKED)
        librarySong("c")

        songs.moveInAlbum("a1", "a", after = "c")
        assertEquals(listOf("c", "a"), songs.listOfAlbum("a1").map { it.songId })

        // Taken back, it is where it was among the others
        songs.update("b") { it.copy(likedAt = null) }
        assertEquals(listOf("b", "c", "a"), songs.listOfAlbum("a1").map { it.songId })
    }

    private suspend fun play(songId: String, at: Long) = database.playbackHistoryDao().insert(
        PlaybackHistoryEntity(
            songId = songId,
            userId = USER,
            timeSpent = 10.seconds,
            createdAt = Instant.fromEpochMilliseconds(at),
        )
    )

    @Test
    fun similarSongsScoreWhatTheyShareWithTheSeed() = runBlocking {
        addArtist("r1")
        addAlbum("a1")
        addSong("seed", albumId = "a1", artists = listOf("r1"))
        addSong("sameArtist", artists = listOf("r1"))
        addSong("playedAround")
        addSong("inPlaylist")
        addSong("sameAlbum", albumId = "a1")
        addSong("likedOnly")
        addSong("unrelated")
        addSong("disliked", artists = listOf("r1"))
        addSong("gone", artists = listOf("r1"), unavailableSince = EPOCH)
        addSong("remote", origin = Origin.YOUTUBE, artists = listOf("r1"))
        listOf(
            "seed", "sameArtist", "playedAround", "inPlaylist", "sameAlbum", "unrelated",
            "gone", "remote",
        ).forEach { librarySong(it) }
        librarySong("likedOnly", liked)
        librarySong("disliked", DISLIKED)

        val hour = 60 * 60 * 1000L
        play("seed", hour)
        play("playedAround", hour + 60_000)
        play("unrelated", 5 * hour)
        val playlistId = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "Mix",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )
        database.userPlaylistDao().addSong(SongPlaylistMapEntity("seed", playlistId, 0))
        database.userPlaylistDao().addSong(SongPlaylistMapEntity("inPlaylist", playlistId, 1))

        // Artist 3, then the play and the playlist 2 each, album and like 1 each, nothing last
        val found = songs.similar("seed", 10).map { it.songId }
        assertEquals("sameArtist", found[0])
        assertEquals(setOf("playedAround", "inPlaylist"), found.subList(1, 3).toSet())
        assertEquals(setOf("sameAlbum", "likedOnly"), found.subList(3, 5).toSet())
        assertEquals(listOf("unrelated"), found.drop(5))
        assertEquals(2, songs.similar("seed", 2).size)
        assertTrue(songs.similar("missing", 10).isEmpty())
    }

    @Test
    fun songsAreSortedIgnoringCaseWithMissingValuesLast() = runBlocking {
        addSong("b", name = "beta")
        addSong("a", name = "Alpha")
        addSong("none", name = null)
        listOf("b", "a", "none").forEach { librarySong(it) }

        assertEquals(listOf("a", "b", "none"), songs.all().asList().map { it.songId })
        assertEquals(
            listOf("b", "a", "none"),
            songs.all(SortOrder(UserSongSort.NAME, SortDirection.DESCENDING)).asList()
                .map { it.songId }
        )
    }

    @Test
    fun theViewPrefersTheUsersOwnNames() = runBlocking {
        addArtist("r1", name = "Catalog artist")
        addArtist("r2", name = "Second")
        addAlbum("a1", name = "Catalog album")
        addSong("s1", name = "Catalog name", albumId = "a1", artists = listOf("r1", "r2"))
        librarySong("s1", name = "My name")
        artists.save(UserArtist("r1", name = "My artist"))
        albums.save(UserAlbum("a1", name = "My album"))

        val view = songs.find("s1").first()!!
        assertEquals("My name", view.name)
        assertEquals(listOf("My artist", "Second"), view.artists)
        assertEquals(listOf("r1", "r2"), view.artistIds)
        assertEquals("My album", view.albumName)
    }

    @Test
    fun aSongWithoutArtistsHasEmptyLists() = runBlocking {
        addSong("s1")
        librarySong("s1")

        val view = songs.find("s1").first()!!
        assertEquals(emptyList<String>(), view.artists)
        assertEquals(emptyList<String>(), view.artistIds)
    }

    @Test
    fun updatingKeepsWhenTheRowWasMade() = runBlocking {
        addSong("s1")
        val created = clock.now()
        songs.update("s1") { it.copy(likedAt = liked) }
        clock.advance()

        songs.update("s1") { it.copy(name = "Mine") }

        val row = database.userSongDao().get(USER, "s1")!!
        assertEquals(liked, row.likedAt)
        assertEquals("Mine", row.name)
        assertEquals(created, row.createdAt)
        assertEquals(clock.now(), row.updatedAt)
    }

    @Test
    fun updatingCannotMoveTheEntryToAnotherSong() {
        runBlocking { addSong("s1") }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { songs.update("s1") { it.copy(songId = "s2") } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { albums.update("a1") { it.copy(albumId = "a2") } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { artists.update("r1") { it.copy(artistId = "r2") } }
        }
    }

    @Test
    fun theLibraryFollowsTheActiveUser() = runBlocking {
        addUser("other")
        addSong("s1")
        librarySong("s1", liked)

        activeUser.id.value = "other"

        assertNull(songs.find("s1").first())
        assertTrue(songs.all().asList().isEmpty())
    }

    @Test
    fun withoutActiveUserTheLibraryIsEmptyAndCannotChange() {
        runBlocking {
            addSong("s1")
            librarySong("s1")
        }
        activeUser.id.value = null

        // The lists are PagingData.empty() then, which a snapshot waits on forever
        assertNull(runBlocking { songs.find("s1").first() })
        assertThrows(IllegalStateException::class.java) {
            runBlocking { songs.save(UserSong("s1")) }
        }
    }

    @Test
    fun removingASongLeavesTheCatalog() = runBlocking {
        addSong("s1")
        librarySong("s1")

        songs.remove("s1")

        assertNull(database.userSongDao().get(USER, "s1"))
        assertNotNull(database.songDao().get("s1"))
    }
    //</editor-fold>

    //<editor-fold desc="Albums and artists">
    @Test
    fun bookmarkedAlbumsAndFollowedArtistsLeaveOutDislikes() = runBlocking {
        listOf("a1", "a2", "a3").forEach { addAlbum(it) }
        albums.save(UserAlbum("a1", bookmarkedAt = liked))
        albums.save(UserAlbum("a2", bookmarkedAt = DISLIKED))
        albums.save(UserAlbum("a3"))
        listOf("r1", "r2").forEach { addArtist(it) }
        artists.save(UserArtist("r1", followSince = liked))
        artists.save(UserArtist("r2", followSince = DISLIKED))

        assertEquals(listOf("a1"), albums.bookmarked().asList().map { it.albumId })
        assertEquals(listOf("r1"), artists.followed().asList().map { it.artistId })
    }

    @Test
    fun albumsAndArtistsCountTheirSongs() = runBlocking {
        addAlbum("a1")
        addArtist("r1")
        addSong("s1", albumId = "a1", artists = listOf("r1"))
        addSong("s2", albumId = "a1", artists = listOf("r1"))
        addSong("s3", albumId = "a1")
        albums.save(UserAlbum("a1"))
        artists.save(UserArtist("r1"))

        assertEquals(3, albums.find("a1").first()?.songCount)
        assertEquals(2, artists.find("r1").first()?.songCount)
    }

    @Test
    fun theAlbumsOfAnArtistAreNewestFirst() = runBlocking {
        addArtist("r1")
        addAlbum("old", year = 1990)
        addAlbum("new", year = 2020)
        addAlbum("unknown", year = null)
        addAlbum("notTheirs", year = 2024)
        addSong("s1", albumId = "old", artists = listOf("r1"))
        addSong("s2", albumId = "new", artists = listOf("r1"))
        addSong("s3", albumId = "new", artists = listOf("r1"))
        addSong("s4", albumId = "unknown", artists = listOf("r1"))
        addSong("s5", albumId = "notTheirs")
        listOf("old", "new", "unknown", "notTheirs").forEach { albums.save(UserAlbum(it)) }

        assertEquals(
            listOf("new", "old", "unknown"),
            albums.ofArtist("r1", 10).first().map { it.albumId })
        assertEquals(listOf("new"), albums.ofArtist("r1", 1).first().map { it.albumId })
    }

    @Test
    fun albumsAndArtistsAreSearchedByName() = runBlocking {
        addAlbum("a1", name = "Abbey Road")
        addAlbum("a2", name = "Revolver")
        addArtist("r1", name = "The Beatles")
        addArtist("r2", name = "Queen")
        albums.save(UserAlbum("a1"))
        albums.save(UserAlbum("a2"))
        artists.save(UserArtist("r1"))
        artists.save(UserArtist("r2"))

        assertEquals(listOf("a1"), albums.search("road").asList().map { it.albumId })
        assertEquals(listOf("r1"), artists.search("beatles").asList().map { it.artistId })
    }

    @Test
    fun albumsAndArtistsAreFilteredByOrigin() = runBlocking {
        addAlbum("localAlbum", origin = Origin.LOCAL)
        addAlbum("tubeAlbum", origin = Origin.YOUTUBE)
        addArtist("localArtist", origin = Origin.LOCAL)
        addArtist("tubeArtist", origin = Origin.YOUTUBE)
        albums.save(UserAlbum("localAlbum"))
        albums.save(UserAlbum("tubeAlbum"))
        artists.save(UserArtist("localArtist"))
        artists.save(UserArtist("tubeArtist"))

        val tube = setOf(Origin.YOUTUBE)
        assertEquals(listOf("tubeAlbum"), albums.all(origins = tube).asList().map { it.albumId })
        assertEquals(listOf("tubeArtist"), artists.all(origins = tube).asList().map { it.artistId })
        assertEquals(listOf("tubeAlbum"), albums.list(origins = tube).map { it.albumId })
        assertEquals(listOf("tubeArtist"), artists.list(origins = tube).map { it.artistId })
        assertEquals(
            listOf("localArtist", "tubeArtist"),
            artists.list().map { it.artistId },
        )
        assertEquals(1, albums.count(tube))
        assertEquals(2, artists.count())
        assertEquals("tubeAlbum", albums.random(tube)?.albumId)
        assertEquals("localArtist", artists.random(setOf(Origin.LOCAL))?.artistId)
        assertNull(albums.random(setOf(Origin.OPEN_SUBSONIC)))
        assertEquals(0, artists.count(setOf(Origin.OPEN_SUBSONIC)))
        assertEquals(setOf(Origin.LOCAL, Origin.YOUTUBE), albums.origins().first())
        assertEquals(setOf(Origin.LOCAL, Origin.YOUTUBE), artists.origins().first())
    }
    //</editor-fold>

    //<editor-fold desc="Albums and artists kept for nothing">
    private val remote = Origin.YOUTUBE

    @Test
    fun anAlbumOrArtistKeptForNothingLeavesTheLibrary() = runBlocking {
        addAlbum("a1", origin = remote)
        addArtist("r1", origin = remote)
        albums.save(UserAlbum("a1"))
        artists.save(UserArtist("r1"))

        assertTrue(albums.removeIfEmpty("a1"))
        assertTrue(artists.removeIfEmpty("r1"))
        assertNull(database.userAlbumDao().get(USER, "a1"))
        assertNull(database.userArtistDao().get(USER, "r1"))
        // Only the library entry goes
        assertNotNull(database.albumDao().get("a1"))
        assertNotNull(database.artistDao().get("r1"))
    }

    @Test
    fun aReactionKeepsTheEntry() = runBlocking {
        listOf("a1", "a2").forEach { addAlbum(it, origin = remote) }
        listOf("r1", "r2").forEach { addArtist(it, origin = remote) }
        albums.save(UserAlbum("a1", bookmarkedAt = liked))
        albums.save(UserAlbum("a2", bookmarkedAt = DISLIKED))
        artists.save(UserArtist("r1", followSince = liked))
        artists.save(UserArtist("r2", followSince = DISLIKED))

        listOf("a1", "a2").forEach { assertFalse(albums.removeIfEmpty(it)) }
        listOf("r1", "r2").forEach { assertFalse(artists.removeIfEmpty(it)) }
        assertEquals(2, albums.count())
        assertEquals(2, artists.count())
    }

    @Test
    fun theUsersOwnValuesKeepTheEntry() = runBlocking {
        listOf("a1", "a2", "a3").forEach { addAlbum(it, origin = remote) }
        listOf("r1", "r2").forEach { addArtist(it, origin = remote) }
        val cover = Uri.parse("https://example.com/cover.jpg")
        albums.save(UserAlbum("a1", name = "Mine"))
        albums.save(UserAlbum("a2", thumbnail = cover))
        albums.save(UserAlbum("a3", artists = listOf("Someone")))
        artists.save(UserArtist("r1", name = "Mine"))
        artists.save(UserArtist("r2", thumbnail = cover))

        listOf("a1", "a2", "a3").forEach { assertFalse(albums.removeIfEmpty(it)) }
        listOf("r1", "r2").forEach { assertFalse(artists.removeIfEmpty(it)) }
        assertEquals(3, albums.count())
        assertEquals(2, artists.count())
    }

    @Test
    fun theDevicesAlbumsAndArtistsStayInTheLibrary() = runBlocking {
        addAlbum("a1", origin = Origin.LOCAL)
        addArtist("r1", origin = Origin.LOCAL)
        albums.save(UserAlbum("a1"))
        artists.save(UserArtist("r1"))

        assertFalse(albums.removeIfEmpty("a1"))
        assertFalse(artists.removeIfEmpty("r1"))
        assertEquals(1, albums.count())
        assertEquals(1, artists.count())
    }

    @Test
    fun anotherUsersEntryStays() = runBlocking {
        addUser("other")
        addAlbum("a1", origin = remote)
        albums.save(UserAlbum("a1"))
        activeUser.id.value = "other"
        albums.save(UserAlbum("a1"))
        activeUser.id.value = USER

        assertTrue(albums.removeIfEmpty("a1"))
        assertNotNull(database.userAlbumDao().get("other", "a1"))
    }
    //</editor-fold>
}
