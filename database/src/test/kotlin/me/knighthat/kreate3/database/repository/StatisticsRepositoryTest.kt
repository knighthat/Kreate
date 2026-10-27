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
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.model.ListeningSummary
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class StatisticsRepositoryTest : DatabaseTest() {

    private lateinit var repository: StatisticsRepositoryImpl
    private lateinit var history: HistoryRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        addUser()
        addUser("other")
        repository = StatisticsRepositoryImpl(database.playbackHistoryDao(), activeUser)
        history = HistoryRepositoryImpl(
            database,
            database.searchHistoryDao(),
            database.playbackHistoryDao(),
            activeUser,
            clock,
        )
    }

    /** A song of [minutes] in the user's library, by [artists], on [albumId]. */
    private suspend fun librarySong(
        id: String,
        minutes: Int?,
        albumId: String? = null,
        artists: List<String> = emptyList(),
        origin: Origin = Origin.LOCAL,
        likedAt: Instant? = null,
    ) {
        database.songDao().upsert(
            SongEntity(
                id = id,
                origin = origin,
                name = id,
                duration = minutes?.minutes,
                albumId = albumId,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
        artists.forEach { database.songDao().link(SongArtistMapEntity(id, it)) }
        database.userSongDao().upsert(
            UserSongEntity(
                userId = USER,
                songId = id,
                likedAt = likedAt,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
    }

    /** Records a play of [songId] for [minutes] at second [at] of the test clock. */
    private suspend fun playAt(songId: String, minutes: Int, at: Long = 0) {
        clock.time = Instant.fromEpochMilliseconds(at * 1000)
        history.recordPlay(songId, minutes.minutes)
    }

    @Test
    fun songsRankByTimesListenedThrough() = runBlocking {
        librarySong("short", minutes = 3)
        librarySong("long", minutes = 10)
        // 9 minutes of a 3 minute song is 3 listens, more than 4 plays of half the long one
        playAt("short", 9)
        repeat(4) { playAt("long", 5) }

        val top = repository.topSongs(null, 5).first()
        assertEquals(listOf("short", "long"), top.map { it.song.songId })
        assertEquals(listOf(3.0, 2.0), top.map { it.listens })
        assertEquals(20.minutes, top.last().timeSpent)
    }

    @Test
    fun aSongOfUnknownLengthCountsAListenPerPlay() = runBlocking {
        librarySong("unknown", minutes = null)
        playAt("unknown", 1)
        playAt("unknown", 2)

        assertEquals(listOf(2.0), repository.topSongs(null, 5).first().map { it.listens })
    }

    @Test
    fun dislikedSongsAndOtherUsersAreLeftOut() = runBlocking {
        librarySong("liked", minutes = 1, likedAt = EPOCH)
        librarySong("disliked", minutes = 1, likedAt = Instant.fromEpochMilliseconds(-1))
        playAt("liked", 1)
        repeat(3) { playAt("disliked", 1) }
        activeUser.id.value = "other"
        database.userSongDao().upsert(
            UserSongEntity(userId = "other", songId = "liked", createdAt = EPOCH, updatedAt = EPOCH)
        )
        repeat(5) { playAt("liked", 1) }
        activeUser.id.value = USER

        assertEquals(listOf("liked"), repository.topSongs(null, 5).first().map { it.song.songId })
        assertEquals(ListeningSummary(1, 1, 0, 1.minutes), repository.summary(null).first())
    }

    @Test
    fun albumsAndArtistsAddUpTheirSongs() = runBlocking {
        addAlbum("a1")
        addAlbum("a2")
        addArtist("solo")
        addArtist("duo")
        librarySong("s1", minutes = 2, albumId = "a1", artists = listOf("solo"))
        librarySong("s2", minutes = 2, albumId = "a1", artists = listOf("solo", "duo"))
        librarySong("s3", minutes = 1, albumId = "a2", artists = listOf("duo"))
        playAt("s1", 2)
        playAt("s2", 2)
        repeat(3) { playAt("s3", 1) }

        val albums = repository.topAlbums(null, 3).first()
        assertEquals(
            listOf("a2" to 3.0, "a1" to 2.0),
            albums.map { it.album.albumId to it.listens })
        // A song with two artists counts for both
        val artists = repository.topArtists(null, 10).first()
        assertEquals(
            listOf("duo" to 4.0, "solo" to 2.0),
            artists.map { it.artist.artistId to it.listens },
        )
        assertEquals(ListeningSummary(5, 3, 2, 7.minutes), repository.summary(null).first())
    }

    @Test
    fun anAlbumAddsUpOnlyItsSongs() = runBlocking {
        addAlbum("a1")
        addAlbum("a2")
        librarySong("s1", minutes = 2, albumId = "a1")
        librarySong("s2", minutes = 1, albumId = "a1")
        librarySong("other", minutes = 1, albumId = "a2")
        playAt("s1", 4, at = 20)
        playAt("s2", 1, at = 10)
        playAt("other", 5)

        val album = repository.album("a1").first()!!
        assertEquals(3.0, album.listens, 0.0)
        assertEquals(5.minutes, album.timeSpent)
        assertEquals(
            listOf("s1", "s2"),
            repository.albumSongs("a1", 10).first().map { it.song.songId })
        assertEquals(
            listOf(1.minutes, 4.minutes),
            repository.albumPlays("a1").first().map { it.timeSpent })
        assertEquals(null, repository.album("unplayed").first())
    }

    @Test
    fun anArtistAddsUpTheSongsCreditingThem() = runBlocking {
        addArtist("solo")
        addArtist("duo")
        librarySong("s1", minutes = 1, artists = listOf("solo"))
        librarySong("s2", minutes = 1, artists = listOf("solo", "duo"))
        librarySong("s3", minutes = 1, artists = listOf("duo"))
        playAt("s1", 1)
        repeat(2) { playAt("s2", 1) }
        repeat(4) { playAt("s3", 1) }

        // Only the artist asked for, not the one sharing a song with them
        val solo = repository.artist("solo").first()!!
        assertEquals("solo" to 3.0, solo.artist.artistId to solo.listens)
        assertEquals(
            listOf("s2", "s1"),
            repository.artistSongs("solo", 10).first().map { it.song.songId })
        assertEquals(3, repository.artistPlays("solo").first().size)
    }

    @Test
    fun aPeriodKeepsOnlyItsPlays() = runBlocking {
        librarySong("old", minutes = 1)
        librarySong("new", minutes = 1)
        playAt("old", 5, at = 10)
        playAt("new", 1, at = 100)
        val period = PlayPeriod(Instant.fromEpochSeconds(50), Instant.fromEpochSeconds(200))

        assertEquals(listOf("new"), repository.topSongs(period, 5).first().map { it.song.songId })
        assertEquals(1, repository.summary(period).first().plays)
    }

    @Test
    fun timeIsSplitByOrigin() = runBlocking {
        librarySong("local", minutes = 1)
        librarySong("yt", minutes = 1, origin = Origin.YOUTUBE)
        playAt("local", 1)
        playAt("yt", 3)

        assertEquals(
            listOf(OriginTime(Origin.YOUTUBE, 3.minutes), OriginTime(Origin.LOCAL, 1.minutes)),
            repository.timeByOrigin(null).first(),
        )
    }

    @Test
    fun songPlaysAreOldestFirst() = runBlocking {
        librarySong("s", minutes = 1)
        playAt("s", 2, at = 20)
        playAt("s", 1, at = 10)

        assertEquals(
            listOf(60.seconds, 120.seconds),
            repository.songPlays("s").first().map { it.timeSpent },
        )
    }
}
