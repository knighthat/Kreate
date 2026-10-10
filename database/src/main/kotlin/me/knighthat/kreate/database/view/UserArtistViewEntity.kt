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

package me.knighthat.kreate.database.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import me.knighthat.kreate.database.model.Origin
import kotlin.time.Instant

/**
 * An artist in one user's library with every value resolved: the user's own value when set,
 * the shared one otherwise. Filter on [userId].
 */
@DatabaseView(
    viewName = "user_artist_view",
    value =
        "SELECT\n" +
                "    uar.user_id AS user_id,\n" +
                "    ar.id AS artist_id,\n" +
                "    ar.origin AS origin,\n" +
                "    COALESCE(uar.name, ar.name) AS name,\n" +
                "    COALESCE(uar.thumbnail, ar.thumbnail) AS thumbnail,\n" +
                "    (SELECT COUNT(*) FROM song_artist_map m WHERE m.artist_id = ar.id) AS song_count,\n" +
                "    uar.follow_since AS follow_since\n" +
                "FROM user_artist uar\n" +
                "JOIN artist ar ON ar.id = uar.artist_id\n",
)
internal data class UserArtistViewEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    val origin: Origin,
    val name: String?,
    val thumbnail: String?,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "follow_since")
    val followSince: Instant?,
)
