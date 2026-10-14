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

package me.knighthat.kreate3.database.paging

/**
 * Which way a sorted list runs.
 */
enum class SortDirection { ASCENDING, DESCENDING }

/**
 * A value a list can be sorted by. Each list has its own enum of fields, see [UserSongSort].
 */
interface SortField {

    /** Column in the query. It is for the database, callers pick a value of the enums. */
    val column: String

    /** Text is compared without regard to case. */
    val isText: Boolean
}

/**
 * How to sort a list: by which field, and which way. Rows with no value come last.
 */
data class SortOrder<S : SortField>(
    val by: S,
    val direction: SortDirection = SortDirection.ASCENDING,
)

/** Fields to sort the songs of the user's library by. */
enum class UserSongSort(override val column: String, override val isText: Boolean = false) :
    SortField {
    NAME("name", true),
    ARTISTS("artists", true),
    ALBUM("album_name", true),
    ALBUM_POSITION("album_position"),
    DURATION("duration"),
    EXPLICIT("is_explicit"),
    ORIGIN("origin", true),
    LIKED_AT("liked_at"),
}

/** Fields to sort the albums of the user's library by. */
enum class UserAlbumSort(override val column: String, override val isText: Boolean = false) :
    SortField {
    NAME("name", true),
    ARTISTS("artists", true),
    YEAR("year"),
    SONG_COUNT("song_count"),
    EXPLICIT("is_explicit"),
    ORIGIN("origin", true),
    BOOKMARKED_AT("bookmarked_at"),
}

/** Fields to sort the artists of the user's library by. */
enum class UserArtistSort(override val column: String, override val isText: Boolean = false) :
    SortField {
    NAME("name", true),
    SONG_COUNT("song_count"),
    ORIGIN("origin", true),
    FOLLOW_SINCE("follow_since"),
}

/** Fields to sort playlists by. */
enum class PlaylistSort(override val column: String, override val isText: Boolean = false) :
    SortField {
    NAME("name", true),
    ORIGIN("origin", true),
    MONTHLY("is_monthly"),
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at"),
}

/** Fields to sort the songs inside an album or a playlist by. */
enum class SongSort(override val column: String, override val isText: Boolean = false) : SortField {
    NAME("song.name", true),
    ALBUM_POSITION("song.album_position"),
    DURATION("song.duration"),
    EXPLICIT("song.is_explicit"),
    ORIGIN("song.origin", true),
}

/** Fields to sort searches by. */
enum class SearchSort(override val column: String, override val isText: Boolean = false) :
    SortField {
    QUERY("query", true),
    LAST_USED("last_used"),
}

/**
 * The `ORDER BY` clause of [this]. [tieBreaker] is a unique column, without it rows with the
 * same value could swap places between pages and show up twice or not at all.
 */
internal fun SortOrder<*>.toOrderBy(tieBreaker: String): String {
    val collation = if (by.isText) " COLLATE NOCASE" else ""
    val way = if (direction == SortDirection.ASCENDING) "ASC" else "DESC"
    return "ORDER BY ${by.column}$collation $way NULLS LAST, $tieBreaker"
}
