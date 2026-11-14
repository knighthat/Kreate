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

package me.knighthat.kreate3.playlist

import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaylistCsvTest {

    private fun parse(text: String) = parsePlaylistCsv(StringReader(text))

    @Test
    fun oldAppFileIsRead() {
        val rows = parse(
            """
            PlaylistBrowseId,PlaylistName,MediaId,Title,Artists,Duration,ThumbnailUrl
            ,Summer,dQw4w9WgXcQ,A song,Someone,212,https://example.com/a.jpg
            ,Summer,1234,On the phone,Me,61,content://media/external/audio/albumart/5
            """.trimIndent()
        )

        assertEquals(2, rows.size)
        val first = rows[0]
        assertEquals("Summer", first.playlistName)
        assertEquals(
            PlaylistSong(
                id = "dQw4w9WgXcQ",
                origin = Origin.YOUTUBE,
                title = "A song",
                artists = "Someone",
                duration = 212.seconds,
                thumbnail = "https://example.com/a.jpg",
                fromOldApp = true,
            ),
            first.song,
        )
        // Digits only: the media index's id of a device song
        assertEquals(Origin.LOCAL, rows[1].song.origin)
    }

    @Test
    fun newFileTakesTheOriginColumn() {
        val rows = parse(
            """
            playlist_remote_id,playlist_name,song_id,name,artists,artist_ids,album_id,album_name,album_year,album_position,duration,thumbnail,origin
            ,Mix,abc,One,"A, B",a1|a2,al1,Album,2020,3,10,https://example.com/a.jpg,OPEN_SUBSONIC
            ,Mix,123,Two,B,,,,0,0,10,,youtube
            ,Mix,x,Three,C,,,,,,10,,LOCAL
            ,Mix,y,Four,D,,,,,,10,,SPOTIFY
            ,Mix,456,Five,E,,,,,,10,,
            """.trimIndent()
        )

        // No origin or an unknown one: null, the import skips the song
        assertEquals(
            listOf(Origin.OPEN_SUBSONIC, Origin.YOUTUBE, Origin.LOCAL, null, null),
            rows.map { it.song.origin },
        )
        assertEquals(
            PlaylistFileRow(
                "Mix",
                PlaylistSong(
                    id = "abc",
                    origin = Origin.OPEN_SUBSONIC,
                    title = "One",
                    artists = "A, B",
                    duration = 10.seconds,
                    thumbnail = "https://example.com/a.jpg",
                    artistIds = listOf("a1", "a2"),
                    albumId = "al1",
                    albumName = "Album",
                    albumYear = 2020,
                    albumPosition = 3,
                ),
            ),
            rows[0],
        )
        // 0 is unknown
        assertNull(rows[1].song.albumYear)
        assertNull(rows[1].song.albumPosition)
        assertEquals(emptyList<String>(), rows[1].song.artistIds)
    }

    @Test
    fun missingRepeatedAndUnknownColumnsFail() {
        val error = assertThrows(InvalidHeaderException::class.java) {
            parse(
                "playlist_remote_id,playlist_name,song_id,NAME,name,artists,artist_ids," +
                        "album_id,album_name,album_year,duration,thumbnail,origin,Extra,\n"
            )
        }

        assertEquals(listOf("album_position"), error.missing)
        assertEquals(listOf("NAME"), error.repeated)
        assertEquals(listOf("Extra", ""), error.unknown)
    }

    @Test
    fun oldAppColumnsWithAnOriginFail() {
        val error = assertThrows(InvalidHeaderException::class.java) {
            parse(
                "PlaylistBrowseId,PlaylistName,MediaId,Title,Artists,Duration,ThumbnailUrl," +
                        "Origin\n,Mix,123,One,A,10,,YOUTUBE\n"
            )
        }

        assertEquals(emptyList<String>(), error.missing)
        assertEquals(listOf("Origin"), error.unknown)
    }

    @Test
    fun missingNewHeadersAreNamed() {
        val error = assertThrows(InvalidHeaderException::class.java) {
            parse("playlist_name,song_id,name,origin\nList,id1,One,LOCAL\n")
        }

        assertEquals(
            listOf(
                "playlist_remote_id", "artists", "artist_ids", "album_id", "album_name",
                "album_year", "album_position", "duration", "thumbnail",
            ),
            error.missing,
        )
    }

    @Test
    fun columnsComeInAnyOrderAndCase() {
        val rows = parse(
            "﻿title,Duration,ThumbnailUrl,MEDIAID,Artists,PlaylistName,playlistbrowseid\n" +
                    "One,1:05,,id1,,List,\n"
        )

        val song = rows.single().song
        assertEquals("id1", song.id)
        assertEquals("One", song.title)
        assertEquals(65.seconds, song.duration)
        assertNull(song.artists)
        assertNull(song.thumbnail)
        assertEquals("List", rows.single().playlistName)
    }

    @Test
    fun durationsInSecondsOrClockForm() {
        assertEquals(212.seconds, parseCsvDuration("212"))
        assertEquals(3.minutes + 32.seconds, parseCsvDuration("3:32"))
        assertEquals(3.minutes + 32.seconds, parseCsvDuration("03:32"))
        assertEquals((3_600 + 15 * 60 + 2).seconds, parseCsvDuration("1:15:02"))
        assertNull(parseCsvDuration("0"))
        assertNull(parseCsvDuration("00:00"))
        assertNull(parseCsvDuration(""))
        assertNull(parseCsvDuration(null))
        assertNull(parseCsvDuration("3:"))
        assertNull(parseCsvDuration("abc"))
        assertNull(parseCsvDuration("-5"))
        assertNull(parseCsvDuration("1:2:3:4:5"))
    }

    @Test
    fun quotedFieldsKeepCommasQuotesAndLineBreaks() {
        val rows = parse(
            "PlaylistBrowseId,PlaylistName,MediaId,Title,Artists,Duration,ThumbnailUrl\r\n" +
                    ",\"Rock, Roll\",id1,\"He said \"\"hi\"\"\",\"A, B\",10,\r\n" +
                    ",List,id2,\"Two\nlines\",,10,\r\n"
        )

        assertEquals(2, rows.size)
        assertEquals("Rock, Roll", rows[0].playlistName)
        assertEquals("He said \"hi\"", rows[0].song.title)
        assertEquals("A, B", rows[0].song.artists)
        assertEquals("Two\nlines", rows[1].song.title)
    }

    @Test
    fun emptyLinesAndShortRowsAreFine() {
        val rows = parse(
            "PlaylistBrowseId,PlaylistName,MediaId,Title,Artists,Duration,ThumbnailUrl\n\n,,id1\n\n"
        )

        val row = rows.single()
        assertNull(row.playlistName)
        assertEquals("id1", row.song.id)
        assertNull(row.song.title)
    }

    @Test
    fun missingHeadersAreNamed() {
        val error = assertThrows(InvalidHeaderException::class.java) {
            parse("PlaylistName,MediaId,Title\nList,id1,One\n")
        }

        assertEquals(
            listOf("PlaylistBrowseId", "Artists", "Duration", "ThumbnailUrl"),
            error.missing,
        )
    }

    @Test
    fun emptyFileHasNoHeaders() {
        val error = assertThrows(InvalidHeaderException::class.java) { parse("") }

        assertEquals(PlaylistCsvColumns.HEADER, error.missing)
    }

    @Test
    fun rowsAreGroupedByNameInOrder() {
        val rows = listOf("B", null, "A", "B", null).mapIndexed { index, name ->
            PlaylistFileRow(name, PlaylistSong("s$index", Origin.YOUTUBE))
        }

        val playlists = groupPlaylistRows(rows, "file")

        assertEquals(listOf("B", "file", "A"), playlists.map { it.name })
        assertEquals(listOf("s0", "s3"), playlists[0].songs.map { it.id })
        assertEquals(listOf("s1", "s4"), playlists[1].songs.map { it.id })
    }

    @Test
    fun unnamedRowsWithoutAFileNameGetANumberedName() {
        val rows = listOf("A", null).map { PlaylistFileRow(it, PlaylistSong("s", Origin.YOUTUBE)) }

        assertEquals(listOf("A", "Playlist-2"), groupPlaylistRows(rows, null).map { it.name })
    }

    @Test
    fun deviceSongsMatchByIdThenTitle() {
        fun song(id: String, name: String, seconds: Int?) =
            Song(id, Origin.LOCAL, name = name, duration = seconds?.seconds)

        val matcher = LocalMatcher(
            listOf(
                song("1", "Alone", 100),
                song("2", "Twin", 100),
                song("3", "Twin", 200),
                song("4", "Renamed", 50),
            )
        )

        fun match(id: String, title: String?, seconds: Int? = null) =
            matcher.match(PlaylistSong(id, Origin.LOCAL, title, duration = seconds?.seconds))?.id

        assertEquals("4", match("4", "Renamed"))
        assertEquals("4", match("4", null))
        // Another device: the id points at another song, the title decides
        assertEquals("1", match("4", " Alone "))
        assertEquals("3", match("99", "Twin", 199))
        assertNull(match("99", "Twin"))
        assertNull(match("99", "Twin", 150))
        assertNull(match("99", "alone"))
    }

    @Test
    fun oldAppDeviceSongsMatchOnlyById() {
        val matcher =
            LocalMatcher(listOf(Song("1", Origin.LOCAL, name = "Alone", duration = 100.seconds)))

        fun match(id: String, title: String?) =
            matcher.matchId(PlaylistSong(id, Origin.LOCAL, title, fromOldApp = true))?.id

        assertEquals("1", match("1", " Alone "))
        assertEquals("1", match("1", null))
        // Not stored under that id: no title fallback
        assertNull(match("99", "Alone"))
        // The id points at another song
        assertNull(match("1", "Other"))
    }

    @Test
    fun csvRecordsKeepQuotedCommasAndLineBreaksAndTakeTheFirstWidth() {
        assertEquals(
            listOf(
                listOf("a", "b, c", "d\ne", ""),
                listOf("", "", "", ""),
                listOf("x", "y", "", ""),
                listOf("x", "y", "z", "w"),
            ),
            readCsv(StringReader("\uFEFFa,\"b, c\",\"d\ne\",\r\n\nx,y\nx,y,z,w,v")).toList(),
        )
    }

    @Test
    fun exportifyFileIsReadAsTracksToMatch() {
        // Exportify's header: more columns than read, a byte order mark, some cells empty
        val rows = parse(
            "\uFEFFTrack URI,ISRC,Track Name,Album Name,Artist Name(s),Release Date,Duration (ms),Popularity,Genres,Tempo\n" +
                    "spotify:track:5cRD,GBUM71903244,\"Solar System\",\"Solar System / Siren\",\"Sub Focus\",2019-07-26,288000,64,\"drum and bass\",173.97\n" +
                    "spotify:track:3RN6,,\"Redemption, Part 2\",Redemption,\"Camo & Krooked;Mefjus\",2026,285257,53,,175\n"
        )

        assertEquals(2, rows.size)
        assertNull(rows[0].playlistName)
        assertEquals(
            PlaylistSong(
                id = "spotify:track:5cRD",
                origin = null,
                title = "Solar System",
                artists = "Sub Focus",
                duration = 288.seconds,
                albumName = "Solar System / Siren",
                albumYear = 2019,
                matchOnYouTube = true,
                artistNames = listOf("Sub Focus"),
                isrc = "GBUM71903244",
            ),
            rows[0].song,
        )
        val second = rows[1].song
        // "Camo & Krooked" is one artist, only ";" splits
        assertEquals(listOf("Camo & Krooked", "Mefjus"), second.artistNames)
        assertEquals("Camo & Krooked, Mefjus", second.artists)
        assertEquals("Redemption, Part 2", second.title)
        assertEquals(2026, second.albumYear)
        assertEquals(285_257L, second.duration?.inWholeMilliseconds)
        assertNull(second.isrc)
    }

    @Test
    fun exportifyFileNamesGiveThePlaylistName() {
        assertEquals("Drum and Bass 2026", exportifyPlaylistName("Drum_and_Bass_2026 - 258 songs"))
        assertEquals(
            "Drum & Bass 2026 Liquicity",
            exportifyPlaylistName("Drum_&_Bass_2026_Liquicity")
        )
        assertEquals("Japan", exportifyPlaylistName("Japan - 1 song"))
        assertNull(exportifyPlaylistName("_"))
        assertNull(exportifyPlaylistName(null))
    }
}
