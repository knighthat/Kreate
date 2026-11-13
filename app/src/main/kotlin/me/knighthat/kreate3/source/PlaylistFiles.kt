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

package me.knighthat.kreate3.source

import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playlist.PlaylistCsvColumns
import kotlin.time.Duration

/**
 * A song as it goes in an exported playlist file: the values its source gave (the `song` row),
 * never a name or picture the user set in the app.
 *
 * @property id id of the song at its [origin]
 * @property title the source's title, empty when it has none
 * @property artists the credited artists' names, empty for one without a name, in credit order
 * @property artistIds the credited artists' ids, in the order of [artists]
 * @property albumId `null` for a song without an album
 * @property albumName `null` when unknown
 * @property albumYear `null` when unknown
 * @property albumPosition the song's place on its album, `null` when unknown
 * @property duration `null` when unknown
 * @property thumbnail address of the source's picture, `null` for none
 */
internal data class ExportedSong(
    val id: String,
    val origin: Origin,
    val title: String,
    val artists: List<String>,
    val artistIds: List<String> = emptyList(),
    val albumId: String? = null,
    val albumName: String? = null,
    val albumYear: Int? = null,
    val albumPosition: Int? = null,
    val duration: Duration?,
    val thumbnail: String?,
)

/** Writes the text of exported playlist files. */
internal object PlaylistFiles {

    /** Extension of the files [writeCsv] writes. */
    const val CSV_EXTENSION = "csv"


    /** Line ending of CSV files (RFC 4180). */
    private const val CSV_LINE = "\r\n"

    /**
     * Writes the songs of [songs] whose origin is in [origins] as CSV, a header row first, with
     * the columns of [PlaylistCsvColumns.HEADER]. `playlist_remote_id` stays empty: only the
     * app's own playlists are exported.
     *
     * @param name the playlist's name, on every row
     */
    fun writeCsv(out: Appendable, name: String, songs: List<ExportedSong>, origins: Set<Origin>) {
        csvRow(out, PlaylistCsvColumns.HEADER)
        songs.filter { it.origin in origins }.forEach { song ->
            csvRow(
                out,
                listOf(
                    "",
                    name,
                    song.id,
                    song.title,
                    song.artists.joinToString(PlaylistCsvColumns.ARTIST_SEPARATOR),
                    song.artistIds.joinToString(PlaylistCsvColumns.ARTIST_ID_SEPARATOR),
                    song.albumId.orEmpty(),
                    song.albumName.orEmpty(),
                    song.albumYear?.toString().orEmpty(),
                    song.albumPosition?.toString().orEmpty(),
                    song.duration?.inWholeSeconds?.toString().orEmpty(),
                    song.thumbnail.orEmpty(),
                    song.origin.name,
                ),
            )
        }
    }

    private fun csvRow(out: Appendable, fields: List<String>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) out.append(',')
            out.append(csvField(field))
        }
        out.append(CSV_LINE)
    }

    /** [value] quoted when it holds a comma, a quote or a line break, its quotes doubled. */
    fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${value.replace("\"", "\"\"")}\""
        else
            value
}
