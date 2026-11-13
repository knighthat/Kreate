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
import kotlin.time.Duration.Companion.seconds

/** Columns of a playlist CSV file, as both the old Kreate app and this one write them. */
internal object PlaylistCsvColumns {
    const val PLAYLIST_BROWSE_ID = "PlaylistBrowseId"
    const val PLAYLIST_NAME = "PlaylistName"
    const val MEDIA_ID = "MediaId"
    const val TITLE = "Title"
    const val ARTISTS = "Artists"
    const val DURATION = "Duration"
    const val THUMBNAIL_URL = "ThumbnailUrl"

    /** Written by this app only: the song's [Origin] by name. */
    const val ORIGIN = "Origin"

    /** What a file must have to be read, `PlaylistBrowseId` is not read. */
    val REQUIRED = listOf(PLAYLIST_NAME, MEDIA_ID, TITLE, ARTISTS, DURATION, THUMBNAIL_URL)
}

/** A playlist file lacks [columns], the names of the columns it must have. */
class MissingColumnsException(val columns: List<String>) :
    IllegalArgumentException("Missing columns: ${columns.joinToString()}")

/**
 * One song of a playlist file.
 *
 * @property playlistName the playlist the song is in, `null` when the file names none
 * @property song the song
 */
data class PlaylistFileRow(val playlistName: String?, val song: PlaylistSong)

/**
 * Reads a playlist CSV file: a header row, then a song a row. Columns may come in any order and
 * names are matched without regard to case; unknown columns are ignored. Empty rows are skipped.
 *
 * The old Kreate app wrote the same columns without `Origin`: a row without one is from it, and
 * its id is of a device song when it is digits only (the media index's id), else a YouTube video,
 * and its songs are marked [PlaylistSong.fromOldApp].
 * An `Origin` naming no known source leaves the song's origin `null`.
 *
 * `Duration` is whole seconds (this app and newer old ones) or `m:ss`, `h:mm:ss` (older ones);
 * `0` and anything else is unknown.
 *
 * @throws MissingColumnsException when the header lacks one of [PlaylistCsvColumns.REQUIRED], or
 * the file is empty
 */
fun parsePlaylistCsv(reader: Reader): List<PlaylistFileRow> {
    val records = readCsv(reader).iterator()
    if (!records.hasNext()) throw MissingColumnsException(PlaylistCsvColumns.REQUIRED)

    val header = records.next().mapIndexed { index, name ->
        // A byte order mark sticks to the first name
        name.removePrefix("﻿").trim().lowercase() to index
    }.toMap()
    val missing = PlaylistCsvColumns.REQUIRED.filter { it.lowercase() !in header }
    if (missing.isNotEmpty()) throw MissingColumnsException(missing)

    fun List<String>.cell(column: String): String? =
        header[column.lowercase()]?.let { getOrNull(it) }?.trim()?.takeIf(String::isNotEmpty)

    val hasOrigin = PlaylistCsvColumns.ORIGIN.lowercase() in header
    val rows = ArrayList<PlaylistFileRow>()
    for (record in records) {
        if (record.all(String::isBlank)) continue

        val id = record.cell(PlaylistCsvColumns.MEDIA_ID).orEmpty()
        val originText = if (hasOrigin) record.cell(PlaylistCsvColumns.ORIGIN) else null
        val origin = if (originText != null)
            Origin.entries.firstOrNull { it.name.equals(originText, ignoreCase = true) }
        else
            legacyOrigin(id)

        rows += PlaylistFileRow(
            playlistName = record.cell(PlaylistCsvColumns.PLAYLIST_NAME),
            song = PlaylistSong(
                id = id,
                origin = origin,
                title = record.cell(PlaylistCsvColumns.TITLE),
                artists = record.cell(PlaylistCsvColumns.ARTISTS),
                duration = parseCsvDuration(record.cell(PlaylistCsvColumns.DURATION)),
                thumbnail = record.cell(PlaylistCsvColumns.THUMBNAIL_URL),
                fromOldApp = !hasOrigin,
            ),
        )
    }
    return rows
}

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
