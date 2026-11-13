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
import me.knighthat.kreate3.database.entity.AlbumEntity
import me.knighthat.kreate3.database.entity.UserArtistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.model.SourceArtist
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

internal class SongRepositoryTest : DatabaseTest() {

    private lateinit var repository: SongRepositoryImpl

    @Before
    fun setUp() {
        repository = SongRepositoryImpl(
            database,
            database.songDao(),
            database.songFormatDao(),
            database.lyricsDao(),
            clock,
        )
    }

    private fun format(songId: String, tag: String = TAG, length: Long? = 100, gain: Float? = -3f) =
        SongFormat(songId, tag, "audio/mpeg", length = length, gain = gain)

    @Test
    fun aMissingSongCannotBeUpdated() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update("none") { it } }
        }
    }

    @Test
    fun updatingCannotChangeTheIdButKeepsWhenTheSongWasMade() = runBlocking {
        addSong("s1")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update("s1") { it.copy(id = "s2") } }
        }

        repository.update("s1") { it.copy(name = "Renamed") }
        val row = database.songDao().get("s1")!!
        assertEquals("Renamed", row.name)
        assertEquals(EPOCH, row.createdAt)
        assertEquals(clock.now(), row.updatedAt)
    }

    @Test
    fun savingAFormatAgainKeepsWhenItWasMade() = runBlocking {
        addSong("s1")
        val created = clock.now()
        repository.saveFormat(format("s1", gain = null))
        clock.advance()

        repository.saveFormat(format("s1"))

        val row = database.songFormatDao().get("s1", TAG)!!
        assertEquals(-3f, row.gain)
        assertEquals(created, row.createdAt)
        assertEquals(clock.now(), row.updatedAt)
    }

    @Test
    fun songsWithoutGainAreThoseNotMeasuredForTheTag() = runBlocking {
        addSong("measured")
        addSong("noGain")
        addSong("otherTag")
        addSong("noFormat")
        addSong("gone", unavailableSince = EPOCH)
        addSong("remote", origin = Origin.OPEN_SUBSONIC)
        repository.saveFormat(format("measured"))
        repository.saveFormat(format("noGain", gain = null))
        repository.saveFormat(format("otherTag", tag = "other"))

        assertEquals(
            setOf("noGain", "otherTag", "noFormat"),
            repository.idsWithoutGain(Origin.LOCAL, TAG).toSet()
        )
    }

    @Test
    fun lengthsAreOnlyOfMeasuredFormats() = runBlocking {
        listOf("s1", "s2", "s3", "s4").forEach { addSong(it) }
        repository.saveFormat(format("s1", length = 100))
        repository.saveFormat(format("s2", length = 200, gain = null))
        repository.saveFormat(format("s3", length = null))
        repository.saveFormat(format("s4", tag = "other", length = 400))

        assertEquals(mapOf("s1" to 100L), repository.formatLengths(TAG))
    }

    @Test
    fun deletingASongDeletesItsFormatsAndLyrics() = runBlocking {
        addSong("s1")
        repository.saveFormat(format("s1"))
        repository.saveLyrics(Lyrics("s1", "la la", LyricsSync.NONE, LyricsProvider.YOUTUBE))

        repository.delete("s1")

        assertEquals(emptyList<SongFormat>(), repository.formatsOf("s1").first())
        assertNull(repository.lyricsOf("s1", LyricsProvider.YOUTUBE).first())
    }

    @Test
    fun lyricsAreStoredPerSource() = runBlocking {
        addSong("s1")
        repository.saveLyrics(Lyrics("s1", "youtube", LyricsSync.NONE, LyricsProvider.YOUTUBE))
        repository.saveLyrics(Lyrics("s1", "lrclib", LyricsSync.LINE, LyricsProvider.LRCLIB))
        // Replaces the ones of the same source only
        repository.saveLyrics(Lyrics("s1", "lrclib again", LyricsSync.LINE, LyricsProvider.LRCLIB))

        assertEquals("youtube", repository.lyricsOf("s1", LyricsProvider.YOUTUBE).first()?.lyrics)
        assertEquals(
            "lrclib again",
            repository.lyricsOf("s1", LyricsProvider.LRCLIB).first()?.lyrics
        )
        assertNull(repository.lyricsOf("s1", LyricsProvider.BETTER_LYRICS).first())

        repository.deleteLyrics("s1")

        assertNull(repository.lyricsOf("s1", LyricsProvider.YOUTUBE).first())
        assertNull(repository.lyricsOf("s1", LyricsProvider.LRCLIB).first())
    }

    @Test
    fun deletingAnAlbumKeepsItsSongs() = runBlocking {
        addAlbum("a1")
        addSong("s1", albumId = "a1")

        database.albumDao().delete("a1")

        assertNull(repository.find("s1").first()?.albumId)
    }

    @Test
    fun artistsAreLinkedAndUnlinked() = runBlocking {
        addArtist("r1")
        addArtist("r2")
        addSong("s1")

        repository.linkArtist("s1", "r1")
        repository.linkArtist("s1", "r1")
        repository.linkArtist("s1", "r2")
        repository.unlinkArtist("s1", "r2")

        assertEquals(listOf("r1"), repository.artistsOf("s1").first().map { it.id })
    }

    @Test
    fun sourceValuesSkipTheUsersOverridesAndFollowTheGivenOrder() = runBlocking {
        addUser()
        addArtist("a1", name = "Shared artist")
        addArtist("a0", name = null)
        database.albumDao().upsert(
            AlbumEntity(
                "al1",
                Origin.LOCAL,
                "Album",
                thumbnail = Uri.parse("https://example.com/album.jpg"),
                year = 2020,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
        // Credited a1 first, though a0 sorts before it
        addSong("s1", name = "Shared title", albumId = "al1", artists = listOf("a1", "a0"))
        addSong("s2", name = "Second")
        database.userSongDao().upsert(
            UserSongEntity(
                userId = USER,
                songId = "s1",
                name = "My title",
                artists = listOf("My artist"),
                thumbnail = Uri.parse("file:///mine.jpg"),
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
        database.userArtistDao().upsert(
            UserArtistEntity(USER, "a1", name = "My name", createdAt = EPOCH, updatedAt = EPOCH)
        )

        val found = repository.sourceValues(listOf("s2", "missing", "s1"))

        assertEquals(listOf("s2", "s1"), found.map { it.song.id })
        val first = found.last()
        assertEquals("Shared title", first.song.name)
        assertEquals(
            listOf(SourceArtist("a1", "Shared artist"), SourceArtist("a0", null)),
            first.artists
        )
        assertEquals("Album", first.albumName)
        assertEquals(2020, first.albumYear)
        assertEquals("https://example.com/album.jpg", first.thumbnail.toString())
        assertEquals(emptyList<SourceArtist>(), found.first().artists)
        assertEquals(null, found.first().albumName)
    }

    private companion object {
        const val TAG = "mp3"
    }
}
