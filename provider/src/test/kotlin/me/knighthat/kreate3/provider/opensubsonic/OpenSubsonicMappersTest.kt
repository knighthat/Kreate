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

package me.knighthat.kreate3.provider.opensubsonic

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.SongFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OpenSubsonicMappersTest {

    private val covers = mutableListOf<String>()

    /** Covers are addresses on a device, here only the ids asked for are kept. */
    private val cover: (String) -> Uri? = { covers += it; null }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    //<editor-fold desc="Albums">
    @Test
    fun anAlbumListsEveryArtist() {
        val (album, starredAt) = json(
            """{"id":"a1","name":"Album","artist":"One & Two","year":2001,"coverArt":"al-1",
                "artists":[{"id":"x","name":"One"},{"id":"y","name":"Two"}],
                "starred":"2024-05-01T10:00:00Z","explicitStatus":"clean"}"""
        ).toAlbum(cover)

        assertEquals(
            Album(
                "a1",
                Origin.OPEN_SUBSONIC,
                "Album",
                null,
                2001,
                listOf("One", "Two"),
                isExplicit = false
            ),
            album
        )
        assertEquals(Instant.parse("2024-05-01T10:00:00Z"), starredAt)
        assertEquals(listOf("al-1"), covers)
    }

    @Test
    fun aSubsonicAlbumHasOneArtistName() {
        val album =
            json("""{"id":"a1","name":"Album","artist":"Solo","artists":[]}""").toAlbum(cover).item

        assertEquals(listOf("Solo"), album.artists)
    }

    @Test
    fun anAlbumWithoutArtistOrYearHasNone() {
        val album = json("""{"id":"a1","title":"From title","year":0}""").toAlbum(cover).item

        assertEquals("From title", album.name)
        assertNull(album.artists)
        assertNull(album.year)
        assertNull(album.isExplicit)
        assertEquals(emptyList<String>(), covers)
    }

    @Test
    fun anUnreadableStarIsNoStar() {
        assertNull(json("""{"id":"a1","starred":"yesterday"}""").toAlbum(cover).starredAt)
    }
    //</editor-fold>

    //<editor-fold desc="Songs">
    @Test
    fun aSongKnowsItsAlbumAndArtists() {
        val song = json(
            """{"id":"s1","title":"Song","duration":215,"track":3,"albumId":"a1","album":"Album",
                "artist":"One","displayAlbumArtist":"Various","year":1999,"coverArt":"c",
                "artists":[{"id":"x","name":"One"},{"id":"y","name":"Two"},{"name":"No id"}],
                "explicitStatus":"explicit","starred":"2024-05-01T10:00:00Z"}"""
        ).toSong(cover)

        assertEquals("s1", song.song.id)
        assertEquals("Song", song.song.name)
        assertEquals(215.seconds, song.song.duration)
        assertEquals(3, song.song.albumPosition)
        assertEquals("a1", song.song.albumId)
        assertEquals(true, song.song.isExplicit)
        assertEquals(
            Album("a1", Origin.OPEN_SUBSONIC, "Album", null, 1999, listOf("Various")),
            song.album
        )
        assertEquals(
            listOf(
                Artist("x", Origin.OPEN_SUBSONIC, "One"),
                Artist("y", Origin.OPEN_SUBSONIC, "Two")
            ),
            song.artists
        )
        assertEquals(Instant.parse("2024-05-01T10:00:00Z"), song.starredAt)
        // The song and its album share the cover
        assertEquals(listOf("c", "c"), covers)
    }

    @Test
    fun aSubsonicSongHasOneArtist() {
        val song =
            json("""{"id":"s1","artistId":"x","artist":"One","album":"Album","albumId":"a1"}""")
                .toSong(cover)

        assertEquals(listOf(Artist("x", Origin.OPEN_SUBSONIC, "One")), song.artists)
        assertEquals(listOf("One"), song.album?.artists)
    }

    @Test
    fun aSongWithoutAlbumOrArtistHasNeither() {
        val song = json("""{"id":"s1","artist":"Name without id","track":0}""").toSong(cover)

        assertNull(song.album)
        assertNull(song.song.albumPosition)
        assertEquals(emptyList<Artist>(), song.artists)
    }
    //</editor-fold>

    @Test
    fun anArtistKeepsItsStar() {
        val (artist, starredAt) = json("""{"id":"r1","name":"Abba","coverArt":"ar-1","starred":"2023-01-02T03:04:05Z"}""")
            .toArtist(cover)

        assertEquals(Artist("r1", Origin.OPEN_SUBSONIC, "Abba"), artist)
        assertEquals(Instant.parse("2023-01-02T03:04:05Z"), starredAt)
        assertEquals(listOf("ar-1"), covers)
    }

    //<editor-fold desc="Formats">
    @Test
    fun formatTakesTheTrackReplayGainAsLoudnessOverTheReference() {
        val format = json(
            """{"id":"s1","contentType":"audio/flac","bitRate":1012,"size":31415926,
                "replayGain":{"trackGain":-7.5,"albumGain":-6,"trackPeak":0.98}}"""
        ).toSongFormat()

        // -7.5 dB brings it to -18 LUFS, so it is at -10.5 LUFS, 3.5 dB over -14 LUFS
        assertEquals(
            SongFormat("s1", "OPEN_SUBSONIC", "audio/flac", 1_012_000, 31_415_926, 3.5f),
            format,
        )
    }

    @Test
    fun formatFallsBackToTheAlbumGain() {
        val format = json("""{"id":"s1","replayGain":{"albumGain":-2}}""").toSongFormat()
        assertEquals(-2f, format.gain!!, 0f)
    }

    @Test
    fun formatWithoutReplayGainHasNoGain() {
        val format = json("""{"id":"s1","replayGain":{}}""").toSongFormat()
        assertEquals(SongFormat("s1", "OPEN_SUBSONIC", "audio/*"), format)
        assertNull(json("""{"id":"s1","bitRate":0,"size":0}""").toSongFormat().gain)
    }
    //</editor-fold>

    @Test
    fun aPlaylistAlwaysHasAName() {
        val playlist = json("""{"id":"p1"}""").toPlaylist(cover)

        assertEquals("", playlist.name)
        assertEquals("p1", playlist.remoteId)
        assertEquals(0L, playlist.id)
    }
}
