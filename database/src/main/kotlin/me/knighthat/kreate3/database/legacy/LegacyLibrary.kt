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

package me.knighthat.kreate3.database.legacy

import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection

/**
 * A database of the old app can't be read: it is not a database, or has no songs table.
 */
class LegacyImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A song of the old `songs` table.
 *
 * @property title may start with `modified:`, the old app's mark for a title the user typed
 * @property artists text shown for the artists, same mark
 * @property likedAt time of the like, `-1` for a dislike, `null` for neither
 * @property totalPlaytime how long it was played in all, in milliseconds, `0` for never
 */
internal data class LegacySong(
    val id: String,
    val title: String?,
    val artists: String?,
    val duration: String?,
    val thumbnail: String?,
    val likedAt: Long?,
    val totalPlaytime: Long,
    val isExplicit: Boolean,
    val isLocal: Boolean,
)

/** An artist of the old `artists` table, [isYouTube] is `false` for one found on the device. */
internal data class LegacyArtist(
    val id: String,
    val name: String?,
    val thumbnail: String?,
    val createdAt: Long?,
    val bookmarkedAt: Long?,
    val isYouTube: Boolean,
)

/** An album of the old `albums` table, [isYouTube] is `false` for one found on the device. */
internal data class LegacyAlbum(
    val id: String,
    val title: String?,
    val thumbnail: String?,
    val year: String?,
    val artists: String?,
    val url: String?,
    val createdAt: Long?,
    val bookmarkedAt: Long?,
    val isYouTube: Boolean,
)

/** A playlist of the old `playlists` table. */
internal data class LegacyPlaylist(
    val id: Long,
    val name: String?,
    val isPinned: Boolean,
    val isMonthly: Boolean,
)

internal data class LegacyPlaylistEntry(val songId: String, val playlistId: Long, val position: Int)

internal data class LegacyAlbumEntry(val songId: String, val albumId: String, val position: Int?)

internal data class LegacySongArtist(val songId: String, val artistId: String)

/** The format the old app last played a song in, [loudness] in dB as YouTube gives it. */
internal data class LegacyFormat(
    val songId: String,
    val itag: Int?,
    val mimeType: String?,
    val bitrate: Long?,
    val length: Long?,
    val loudness: Float?,
)

/** A play of the old `playback_history`, [createdAt] and [timeSpent] in milliseconds. */
internal data class LegacyPlay(val songId: String, val createdAt: Long, val timeSpent: Long)

/** A search of the old `search_history`, a higher [id] is a later search. */
internal data class LegacySearch(val query: String, val id: Long)

/**
 * What one old database holds that the app keeps. The old queue and lyrics are left out, see
 * [LegacyMapper].
 */
internal data class LegacyLibrary(
    val songs: List<LegacySong> = emptyList(),
    val artists: List<LegacyArtist> = emptyList(),
    val albums: List<LegacyAlbum> = emptyList(),
    val playlists: List<LegacyPlaylist> = emptyList(),
    val playlistEntries: List<LegacyPlaylistEntry> = emptyList(),
    val albumEntries: List<LegacyAlbumEntry> = emptyList(),
    val songArtists: List<LegacySongArtist> = emptyList(),
    val formats: List<LegacyFormat> = emptyList(),
    val plays: List<LegacyPlay> = emptyList(),
    val searches: List<LegacySearch> = emptyList(),
)

/** One row by column name, a value is a `Long`, a `Double`, a `String` or `null`. */
private class Row(private val values: Map<String, Any?>) {

    fun text(name: String): String? = values[name] as? String

    fun long(name: String): Long? = (values[name] as? Number)?.toLong()

    fun double(name: String): Double? = (values[name] as? Number)?.toDouble()

    fun flag(name: String): Boolean = (long(name) ?: 0L) != 0L
}

private fun SQLiteConnection.columnsOf(table: String): Set<String> =
    prepare("PRAGMA table_info(`$table`)").use { statement ->
        buildSet { while (statement.step()) add(statement.getText(1)) }
    }

/**
 * Rows of [table] with the [wanted] columns it has. A column the table lacks, like one an older
 * version of the old app didn't have yet, is missing from the rows; a missing table gives none.
 */
private fun SQLiteConnection.rows(
    table: String,
    wanted: List<String>,
    orderBy: String? = null,
): List<Row> {
    val present = columnsOf(table)
    val columns = wanted.filter { it in present }
    if (columns.isEmpty()) return emptyList()

    val order = orderBy?.takeIf { it in present }?.let { " ORDER BY `$it`" }.orEmpty()
    val sql = "SELECT ${columns.joinToString(", ") { "`$it`" }} FROM `$table`$order"
    return prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    Row(
                        columns.indices.associate { index ->
                            columns[index] to when (statement.getColumnType(index)) {
                                SQLITE_DATA_INTEGER -> statement.getLong(index)
                                SQLITE_DATA_FLOAT -> statement.getDouble(index)
                                SQLITE_DATA_TEXT -> statement.getText(index)
                                else -> null
                            }
                        }
                    )
                )
            }
        }
    }
}

/**
 * Reads the old app's database on [this]. The old schema changed through 36 versions, so this
 * reads by column name and takes what is there instead of looking at the version.
 *
 * @throws LegacyImportException when there is no `songs` table with ids
 */
internal fun SQLiteConnection.readLegacyLibrary(): LegacyLibrary {
    if ("id" !in columnsOf("songs"))
        throw LegacyImportException("The database has no songs table")

    return LegacyLibrary(
        songs = rows(
            "songs",
            listOf(
                "id", "title", "artists", "duration", "thumbnail_url", "liked_at", "total_playtime",
                "is_explicit", "is_local"
            ),
        ).mapNotNull {
            LegacySong(
                id = it.text("id") ?: return@mapNotNull null,
                title = it.text("title"),
                artists = it.text("artists"),
                duration = it.text("duration"),
                thumbnail = it.text("thumbnail_url"),
                likedAt = it.long("liked_at"),
                totalPlaytime = it.long("total_playtime") ?: 0,
                isExplicit = it.flag("is_explicit"),
                isLocal = it.flag("is_local"),
            )
        },
        artists = rows(
            "artists",
            listOf("id", "name", "thumbnail_url", "created_at", "bookmarked_at", "youtube"),
        ).mapNotNull {
            LegacyArtist(
                id = it.text("id") ?: return@mapNotNull null,
                name = it.text("name"),
                thumbnail = it.text("thumbnail_url"),
                createdAt = it.long("created_at"),
                bookmarkedAt = it.long("bookmarked_at"),
                isYouTube = it.flag("youtube"),
            )
        },
        albums = rows(
            "albums",
            listOf(
                "id", "title", "thumbnail_url", "year", "artists", "url", "created_at",
                "bookmarked_at", "youtube"
            ),
        ).mapNotNull {
            LegacyAlbum(
                id = it.text("id") ?: return@mapNotNull null,
                title = it.text("title"),
                thumbnail = it.text("thumbnail_url"),
                year = it.text("year"),
                artists = it.text("artists"),
                url = it.text("url"),
                createdAt = it.long("created_at"),
                bookmarkedAt = it.long("bookmarked_at"),
                isYouTube = it.flag("youtube"),
            )
        },
        playlists = rows("playlists", listOf("id", "name", "is_pinned", "is_monthly"), "id")
            .mapNotNull {
                LegacyPlaylist(
                    id = it.long("id") ?: return@mapNotNull null,
                    name = it.text("name"),
                    isPinned = it.flag("is_pinned"),
                    isMonthly = it.flag("is_monthly"),
                )
            },
        playlistEntries = rows("song_playlist_map", listOf("song_id", "playlist_id", "position"))
            .mapNotNull {
                LegacyPlaylistEntry(
                    songId = it.text("song_id") ?: return@mapNotNull null,
                    playlistId = it.long("playlist_id") ?: return@mapNotNull null,
                    position = it.long("position")?.toInt() ?: 0,
                )
            },
        albumEntries = rows("song_album_map", listOf("song_id", "album_id", "position"))
            .mapNotNull {
                LegacyAlbumEntry(
                    songId = it.text("song_id") ?: return@mapNotNull null,
                    albumId = it.text("album_id") ?: return@mapNotNull null,
                    position = it.long("position")?.toInt(),
                )
            },
        songArtists = rows("song_artist_map", listOf("song_id", "artist_id"))
            .mapNotNull {
                LegacySongArtist(
                    songId = it.text("song_id") ?: return@mapNotNull null,
                    artistId = it.text("artist_id") ?: return@mapNotNull null,
                )
            },
        formats = rows(
            "formats",
            listOf("song_id", "yt_itag", "mimetype", "bitrate", "length", "loudness"),
        ).mapNotNull {
            LegacyFormat(
                songId = it.text("song_id") ?: return@mapNotNull null,
                itag = it.long("yt_itag")?.toInt(),
                mimeType = it.text("mimetype"),
                bitrate = it.long("bitrate"),
                length = it.long("length"),
                loudness = it.double("loudness")?.toFloat(),
            )
        },
        plays = rows("playback_history", listOf("song_id", "created_at", "time_spent"))
            .mapNotNull {
                LegacyPlay(
                    songId = it.text("song_id") ?: return@mapNotNull null,
                    createdAt = it.long("created_at") ?: return@mapNotNull null,
                    timeSpent = it.long("time_spent") ?: 0L,
                )
            },
        searches = rows("search_history", listOf("query", "id"), "id").mapNotNull {
            LegacySearch(
                query = it.text("query")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                id = it.long("id") ?: 0L,
            )
        },
    )
}
