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

package me.knighthat.kreate.database.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.knighthat.kreate.database.model.Origin
import kotlin.time.Instant

/**
 * Artist shared by every user. Per-user overrides live in [UserArtistEntity].
 *
 * @property id id of the artist at its [origin]
 * @property origin where the artist comes from
 * @property name name of the artist, `null` when unknown
 * @property thumbnail image of the artist, `null` for none
 * @property createdAt when the artist was first stored
 * @property updatedAt when the artist was last changed
 */
@Entity(tableName = "artist")
internal data class ArtistEntity(
    @PrimaryKey
    val id: String,
    val origin: Origin,
    val name: String? = null,
    val thumbnail: Uri? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
