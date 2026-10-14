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

package me.knighthat.kreate3.database.view

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import me.knighthat.kreate3.database.model.Origin
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A song in one user's library with every value resolved: the user's own value when set,
 * the shared one otherwise. Filter on [userId].
 *
 * Songs a scan missed (`unavailable_since` is set) are left out, they come back once a scan
 * finds them again. Their rows and the users' data stay in the tables.
 */
@DatabaseView(
    viewName = "user_song_view",
    value =
        "SELECT\n" +
                "    us.user_id AS user_id,\n" +
                "    s.id AS song_id,\n" +
                "    s.origin AS origin,\n" +
                "    COALESCE(us.name, s.name) AS name,\n" +
                "    COALESCE(\n" +
                "        us.artists,\n" +
                "        (SELECT json_group_array(COALESCE(ua.name, ar.name)) FILTER (WHERE COALESCE(ua.name, ar.name) IS NOT NULL)\n" +
                "            FROM song_artist_map m\n" +
                "            JOIN artist ar ON ar.id = m.artist_id\n" +
                "            LEFT JOIN user_artist ua ON ua.artist_id = ar.id AND ua.user_id = us.user_id\n" +
                "            WHERE m.song_id = s.id)\n" +
                "    ) AS artists,\n" +
                "    (SELECT json_group_array(m.artist_id) FROM song_artist_map m WHERE m.song_id = s.id) AS artist_ids,\n" +
                "    COALESCE(us.thumbnail, s.thumbnail, ual.thumbnail, al.thumbnail) AS thumbnail,\n" +
                "    s.duration AS duration,\n" +
                "    s.is_explicit AS is_explicit,\n" +
                "    s.album_id AS album_id,\n" +
                "    COALESCE(ual.name, al.name) AS album_name,\n" +
                "    s.album_position AS album_position,\n" +
                "    s.unavailable_since AS unavailable_since,\n" +
                "    us.liked_at AS liked_at\n" +
                "FROM user_song us\n" +
                "JOIN song s ON s.id = us.song_id\n" +
                "LEFT JOIN album al ON al.id = s.album_id\n" +
                "LEFT JOIN user_album ual ON ual.album_id = s.album_id AND ual.user_id = us.user_id\n" +
                "WHERE s.unavailable_since IS NULL\n",
)
internal data class UserSongViewEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "song_id")
    val songId: String,
    val origin: Origin,
    val name: String?,
    val artists: List<String>?,
    @ColumnInfo(name = "artist_ids")
    val artistIds: List<String>?,
    val thumbnail: Uri?,
    val duration: Duration?,
    @ColumnInfo(name = "is_explicit")
    val isExplicit: Boolean?,
    @ColumnInfo(name = "album_id")
    val albumId: String?,
    @ColumnInfo(name = "album_name")
    val albumName: String?,
    @ColumnInfo(name = "album_position")
    val albumPosition: Int?,
    @ColumnInfo(name = "unavailable_since")
    val unavailableSince: Instant?,
    @ColumnInfo(name = "liked_at")
    val likedAt: Instant?,
)
