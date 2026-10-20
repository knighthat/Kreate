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
import me.knighthat.kreate3.model.Origin
import kotlin.time.Instant

/**
 * An album in one user's library with every value resolved: the user's own value when set,
 * the shared one otherwise. Filter on [userId].
 */
@DatabaseView(
    viewName = "user_album_view",
    value =
        "SELECT\n" +
                "    ual.user_id AS user_id,\n" +
                "    a.id AS album_id,\n" +
                "    a.origin AS origin,\n" +
                "    COALESCE(ual.name, a.name) AS name,\n" +
                "    COALESCE(ual.artists, a.artists) AS artists,\n" +
                "    COALESCE(ual.thumbnail, a.thumbnail) AS thumbnail,\n" +
                "    a.year AS year,\n" +
                "    a.url AS url,\n" +
                "    a.is_explicit AS is_explicit,\n" +
                "    (SELECT COUNT(*) FROM song s WHERE s.album_id = a.id) AS song_count,\n" +
                "    ual.bookmarked_at AS bookmarked_at\n" +
                "FROM user_album ual\n" +
                "JOIN album a ON a.id = ual.album_id\n",
)
internal data class UserAlbumViewEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "album_id")
    val albumId: String,
    val origin: Origin,
    val name: String?,
    val artists: List<String>?,
    val thumbnail: Uri?,
    val year: Int?,
    val url: String?,
    @ColumnInfo(name = "is_explicit")
    val isExplicit: Boolean?,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Instant?,
)
