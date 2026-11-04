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
 * An artist in one user's library with every value resolved: the user's own value when set,
 * the shared one otherwise. Filter on [userId].
 */
@DatabaseView(
    viewName = "user_artist_view",
    value = """
        SELECT
            uar.user_id AS user_id,
            ar.id AS artist_id,
            ar.origin AS origin,
            COALESCE(uar.name, ar.name) AS name,
            COALESCE(uar.thumbnail, ar.thumbnail) AS thumbnail,
            (SELECT COUNT(*) FROM song_artist_map m WHERE m.artist_id = ar.id) AS song_count,
            uar.follow_since AS follow_since
        FROM user_artist uar
        JOIN artist ar ON ar.id = uar.artist_id
    """,
)
internal data class UserArtistViewEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    val origin: Origin,
    val name: String?,
    val thumbnail: Uri?,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "follow_since")
    val followSince: Instant?,
)
