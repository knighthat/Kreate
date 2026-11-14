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

import com.jsoizo.kotlincsv.csvReader
import com.jsoizo.kotlincsv.reader.ExcessFieldsRowBehaviour
import com.jsoizo.kotlincsv.reader.InsufficientFieldsRowBehaviour
import me.knighthat.kreate3.model.Origin
import java.io.Reader
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Columns of the playlist CSV files this app writes. They differ from the old Kreate app's
 * ([LegacyCsvColumns]) on purpose, so the old app doesn't take these files. Files are read by
 * these names: never rename them.
 */
internal object PlaylistCsvColumns {
    /** The playlist's id at its source, empty for one made in the app. Not read. */
    const val PLAYLIST_REMOTE_ID = "playlist_remote_id"
    const val PLAYLIST_NAME = "playlist_name"
    const val SONG_ID = "song_id"
    const val NAME = "name"
    const val ARTISTS = "artists"

    /** Ids of the [ARTISTS] at the song's source, in the same order, joined by `|`. */
    const val ARTIST_IDS = "artist_ids"

    /** The album's id at the song's source. */
    const val ALBUM_ID = "album_id"
    const val ALBUM_NAME = "album_name"

    /** The album's release year. */
    const val ALBUM_YEAR = "album_year"

    /** The song's place on its album. */
    const val ALBUM_POSITION = "album_position"
    const val DURATION = "duration"
    const val THUMBNAIL = "thumbnail"

    /** The song's [Origin] by name. */
    const val ORIGIN = "origin"

    /** How [ARTIST_IDS] are joined; ids never hold it. */
    const val ARTIST_ID_SEPARATOR = "|"

    /** How the names of [ARTISTS] are joined, as the old app did. */
    const val ARTIST_SEPARATOR = ", "

    /** The header row, in the order the columns are written. A file must have all of them. */
    val HEADER = listOf(
        PLAYLIST_REMOTE_ID, PLAYLIST_NAME, SONG_ID, NAME, ARTISTS, ARTIST_IDS, ALBUM_ID,
        ALBUM_NAME, ALBUM_YEAR, ALBUM_POSITION, DURATION, THUMBNAIL, ORIGIN,
    )
}

/** Columns of the old Kreate app's playlist CSV files, read on import only. */
internal object LegacyCsvColumns {
    /** Not read. */
    const val PLAYLIST_BROWSE_ID = "PlaylistBrowseId"
    const val PLAYLIST_NAME = "PlaylistName"
    const val MEDIA_ID = "MediaId"
    const val TITLE = "Title"
    const val ARTISTS = "Artists"
    const val DURATION = "Duration"
    const val THUMBNAIL_URL = "ThumbnailUrl"

    /** The old app's header row. A file must have all of them, like the old app asked. */
    val HEADER = listOf(
        PLAYLIST_BROWSE_ID, PLAYLIST_NAME, MEDIA_ID, TITLE, ARTISTS, DURATION, THUMBNAIL_URL,
    )
}

/**
 * Columns of the CSV files Exportify (exportify.app) writes of a Spotify playlist, read on import
 * only. Exportify adds columns now and then (audio features, genres), so only these are read and
 * any others are left alone.
 */
internal object ExportifyCsvColumns {
    /** `spotify:track:<id>`, or `spotify:local:…` for a file of the user's. */
    const val TRACK_URI = "Track URI"
    const val TRACK_NAME = "Track Name"

    /** The artists' names joined by [ARTIST_SEPARATOR]. */
    const val ARTIST_NAMES = "Artist Name(s)"
    const val DURATION_MS = "Duration (ms)"
    const val ALBUM_NAME = "Album Name"

    /** `2019-07-26`, sometimes only the year. */
    const val RELEASE_DATE = "Release Date"
    const val ISRC = "ISRC"

    /** How [ARTIST_NAMES] are joined; a name like "Camo & Krooked" is one artist. */
    const val ARTIST_SEPARATOR = ";"

    /** The columns that make a file an Exportify one, it must have all of them. */
    val REQUIRED = listOf(TRACK_URI, TRACK_NAME, ARTIST_NAMES, DURATION_MS)
}

/**
 * The header of a playlist file isn't one this app reads: it lacks columns, has one more than
 * once, or has columns no playlist file has.
 *
 * @property missing the names of the columns it must have but lacks
 * @property repeated the columns it has more than once, as the file spells them
 * @property unknown the columns of neither kind of file, as the file spells them
 */
class InvalidHeaderException(
    val missing: List<String> = emptyList(),
    val repeated: List<String> = emptyList(),
    val unknown: List<String> = emptyList(),
) : IllegalArgumentException(
    listOf("missing" to missing, "repeated" to repeated, "unknown" to unknown)
        .filter { (_, columns) -> columns.isNotEmpty() }
        // A blank name would show as nothing at all
        .joinToString("; ", prefix = "Not a playlist file, ") { (kind, columns) ->
            "$kind columns: " + columns.joinToString { it.ifBlank { "\"\"" } }
        }
)

/**
 * One song of a playlist file.
 *
 * @property playlistName the playlist the song is in, `null` when the file names none
 * @property song the song
 */
data class PlaylistFileRow(val playlistName: String?, val song: PlaylistSong)

/**
 * Reads a playlist CSV file: a header row, then a song a row. Columns may come in any order and
 * names are matched without regard to case. Empty rows are skipped.
 *
 * A file with every one of [ExportifyCsvColumns.REQUIRED] is an Exportify file of a Spotify
 * playlist: its tracks are [PlaylistSong.matchOnYouTube], with no playlist name, and only the
 * columns of [ExportifyCsvColumns] are read. Any other file has exactly this app's columns
 * ([PlaylistCsvColumns.HEADER]) or the old Kreate app's ([LegacyCsvColumns.HEADER]): which kind
 * is the one its header has more of, this app's on a tie, and a header lacking one of its
 * columns, having one twice or having any other fails.
 * The old app's files don't say where a song is from: an id of digits only is a device song (the
 * media index's id), anything else a YouTube video, and the songs are marked
 * [PlaylistSong.fromOldApp]. In this app's files a song whose `origin` is empty or names no known
 * source has a `null` origin, so it is skipped.
 *
 * `Duration` is whole seconds (this app and newer old ones) or `m:ss`, `h:mm:ss` (older ones);
 * `0` and anything else is unknown.
 *
 * @throws InvalidHeaderException when the header isn't one of either kind, or the file is empty
 */
fun parsePlaylistCsv(reader: Reader): List<PlaylistFileRow> {
    val records = readCsv(reader).iterator()
    if (!records.hasNext()) throw InvalidHeaderException(missing = PlaylistCsvColumns.HEADER)

    // A byte order mark sticks to the first name
    val names = records.next().map { it.removePrefix("\uFEFF").trim() }
    val header = names.mapIndexed { index, name -> name.lowercase() to index }.toMap()
    val present = names.mapTo(HashSet()) { it.lowercase() }
    val exportify = ExportifyCsvColumns.REQUIRED.all { it.lowercase() in present }
    val legacy = !exportify && checkHeader(names)

    fun List<String>.cell(column: String): String? =
        header[column.lowercase()]?.let { getOrNull(it) }?.trim()?.takeIf(String::isNotEmpty)

    val rows = ArrayList<PlaylistFileRow>()
    for (record in records) {
        if (record.all(String::isBlank)) continue

        rows += if (exportify) {
            val artists = record.cell(ExportifyCsvColumns.ARTIST_NAMES)
                ?.split(ExportifyCsvColumns.ARTIST_SEPARATOR)
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
            PlaylistFileRow(
                playlistName = null,
                song = PlaylistSong(
                    id = record.cell(ExportifyCsvColumns.TRACK_URI).orEmpty(),
                    origin = null,
                    title = record.cell(ExportifyCsvColumns.TRACK_NAME),
                    artists = artists.joinToString(PlaylistCsvColumns.ARTIST_SEPARATOR)
                        .takeIf(String::isNotEmpty),
                    duration = record.cell(ExportifyCsvColumns.DURATION_MS)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.milliseconds,
                    albumName = record.cell(ExportifyCsvColumns.ALBUM_NAME),
                    albumYear = record.cell(ExportifyCsvColumns.RELEASE_DATE)
                        ?.take(4)
                        .positiveInt(),
                    matchOnYouTube = true,
                    artistNames = artists,
                    isrc = record.cell(ExportifyCsvColumns.ISRC),
                ),
            )
        } else if (legacy) {
            val id = record.cell(LegacyCsvColumns.MEDIA_ID).orEmpty()
            PlaylistFileRow(
                playlistName = record.cell(LegacyCsvColumns.PLAYLIST_NAME),
                song = PlaylistSong(
                    id = id,
                    origin = legacyOrigin(id),
                    title = record.cell(LegacyCsvColumns.TITLE),
                    artists = record.cell(LegacyCsvColumns.ARTISTS),
                    duration = parseCsvDuration(record.cell(LegacyCsvColumns.DURATION)),
                    thumbnail = record.cell(LegacyCsvColumns.THUMBNAIL_URL),
                    fromOldApp = true,
                ),
            )
        } else {
            val id = record.cell(PlaylistCsvColumns.SONG_ID).orEmpty()
            val originText = record.cell(PlaylistCsvColumns.ORIGIN)
            val origin =
                Origin.entries.firstOrNull { it.name.equals(originText, ignoreCase = true) }
            PlaylistFileRow(
                playlistName = record.cell(PlaylistCsvColumns.PLAYLIST_NAME),
                song = PlaylistSong(
                    id = id,
                    origin = origin,
                    title = record.cell(PlaylistCsvColumns.NAME),
                    artists = record.cell(PlaylistCsvColumns.ARTISTS),
                    duration = parseCsvDuration(record.cell(PlaylistCsvColumns.DURATION)),
                    thumbnail = record.cell(PlaylistCsvColumns.THUMBNAIL),
                    artistIds = record.cell(PlaylistCsvColumns.ARTIST_IDS)
                        ?.split(PlaylistCsvColumns.ARTIST_ID_SEPARATOR)
                        ?.map(String::trim)
                        ?.filter(String::isNotEmpty)
                        .orEmpty(),
                    albumId = record.cell(PlaylistCsvColumns.ALBUM_ID),
                    albumName = record.cell(PlaylistCsvColumns.ALBUM_NAME),
                    albumYear = record.cell(PlaylistCsvColumns.ALBUM_YEAR).positiveInt(),
                    albumPosition = record.cell(PlaylistCsvColumns.ALBUM_POSITION).positiveInt(),
                    fromOldApp = false,
                ),
            )
        }
    }
    return rows
}

/**
 * The name of the playlist of an Exportify file named [fileName] (without its extension):
 * Exportify writes the playlist's name with `_` for spaces, and a name like "Mix - 258 songs" has
 * its count taken off. `null` when [fileName] is.
 */
fun exportifyPlaylistName(fileName: String?): String? = fileName
    ?.replace('_', ' ')
    ?.replace(SONG_COUNT_SUFFIX, "")
    ?.trim()
    ?.takeIf(String::isNotEmpty)

/** " - 258 songs" at the end of an Exportify file's name. */
private val SONG_COUNT_SUFFIX = Regex("\\s+-\\s+\\d+\\s+songs?$", RegexOption.IGNORE_CASE)

/**
 * Groups [rows] into playlists, in the order their names first show up. Rows without a name go
 * to one named [unnamed], or `Playlist-` and its place among the file's playlists, from 1, like
 * the old app's unnamed playlists are named on import, when [unnamed] is `null`.
 */
fun groupPlaylistRows(rows: List<PlaylistFileRow>, unnamed: String?): List<PlaylistToImport> {
    val groups = LinkedHashMap<String?, MutableList<PlaylistSong>>()
    rows.forEach { groups.getOrPut(it.playlistName) { ArrayList() } += it.song }
    return groups.entries.mapIndexed { index, (name, songs) ->
        PlaylistToImport(name ?: unnamed ?: "Playlist-${index + 1}", songs)
    }
}

/**
 * Checks [names], the header of a file, see [parsePlaylistCsv].
 *
 * @return whether it is the old app's
 * @throws InvalidHeaderException when it isn't one of either kind
 */
private fun checkHeader(names: List<String>): Boolean {
    val present = names.mapTo(HashSet()) { it.lowercase() }
    fun count(columns: List<String>) = columns.count { it.lowercase() in present }
    val legacy = count(LegacyCsvColumns.HEADER) > count(PlaylistCsvColumns.HEADER)
    val expected = if (legacy) LegacyCsvColumns.HEADER else PlaylistCsvColumns.HEADER
    val known = expected.mapTo(HashSet()) { it.lowercase() }

    val missing = expected.filter { it.lowercase() !in present }
    val repeated = names.groupBy { it.lowercase() }.values
        .filter { it.size > 1 }
        .map { it.first() }
    val unknown = names.filter { it.lowercase() !in known }.distinctBy { it.lowercase() }
    if (missing.isNotEmpty() || repeated.isNotEmpty() || unknown.isNotEmpty())
        throw InvalidHeaderException(missing, repeated, unknown)
    return legacy
}

/** A count or year above 0, `null` for anything else: 0 is how sources say they don't know. */
private fun String?.positiveInt(): Int? = this?.toIntOrNull()?.takeIf { it > 0 }

/** The origin of a song of the old app's file, which doesn't say: see [parsePlaylistCsv]. */
private fun legacyOrigin(id: String): Origin =
    if (id.isNotEmpty() && id.all(Char::isDigit)) Origin.LOCAL else Origin.YOUTUBE

/**
 * Whole seconds, or up to four parts split by `:` (`m:ss`, `h:mm:ss`, `d:h:mm:ss`); `null` for
 * anything else and for `0`.
 */
internal fun parseCsvDuration(text: String?): Duration? {
    val parts = text?.split(':') ?: return null
    if (parts.size > 4 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null

    val factors = listOf(1L, 60L, 3_600L, 86_400L)
    val seconds = parts.reversed().foldIndexed(0L) { index, total, part ->
        total + (part.toLongOrNull() ?: return null) * factors[index]
    }
    return seconds.takeIf { it > 0 }?.seconds
}

/** Lenient about row widths: files from other apps may mix rows of different widths. */
private val CSV_READER = csvReader {
    insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.EMPTY_STRING
    excessFieldsRowBehaviour = ExcessFieldsRowBehaviour.TRIM
}

/**
 * The records of a CSV text as RFC 4180 has them, read by kotlin-csv: fields split by `,`, records
 * by CR, LF or CRLF; a field in double quotes may hold commas, line breaks and `""` for a quote. A
 * leading byte order mark is dropped. Every record is as wide as the first one: shorter ones are
 * padded with empty fields (a blank line becomes all empty fields), longer ones cut.
 *
 * @throws com.jsoizo.kotlincsv.exceptions.MalformedCsvException when the text breaks the format,
 * e.g. a quote that never closes
 */
internal fun readCsv(reader: Reader): Sequence<List<String>> {
    val chars = generateSequence { reader.read().takeIf { it >= 0 }?.toChar() }
        .withIndex()
        .filterNot { (index, char) -> index == 0 && char == '\uFEFF' }
        .map { it.value }
    return CSV_READER.read(chars)
}
