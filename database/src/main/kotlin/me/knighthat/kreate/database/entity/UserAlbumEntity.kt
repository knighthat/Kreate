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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.time.Instant

/**
 * What one user keeps about an [AlbumEntity]. [name], [thumbnail] and [artists] override the shared values.
 *
 * @property userId owner of this row
 * @property albumId the [AlbumEntity] this row is about
 * @property name title preferred by the user, `null` to use the shared one
 * @property thumbnail cover preferred by the user, `null` to use the shared one
 * @property artists artist names preferred by the user, `null` to use the shared ones
 * @property bookmarkedAt when the user bookmarked the album, `null` if not bookmarked
 * @property createdAt when the row was first stored
 * @property updatedAt when the row was last changed
 */
@Entity(
    tableName = "user_album",
    primaryKeys = ["user_id", "album_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("album_id")],
)
internal data class UserAlbumEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "album_id")
    val albumId: String,
    val name: String? = null,
    val thumbnail: String? = null,
    val artists: List<String>? = null,
    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
