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

package me.knighthat.kreate3.database.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.time.Instant

/**
 * What one user keeps about a [SongEntity]. [name], [artists] and [thumbnail] override the shared values.
 *
 * @property userId owner of this row
 * @property songId the [SongEntity] this row is about
 * @property name title preferred by the user, `null` to use the shared one
 * @property artists artist names preferred by the user, `null` to use the shared ones
 * @property thumbnail image preferred by the user, `null` to use the shared one
 * @property likedAt when the user liked the song, `null` if not liked
 * @property createdAt when the row was first stored
 * @property updatedAt when the row was last changed
 */
@Entity(
    tableName = "user_song",
    primaryKeys = ["user_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("song_id"), Index("user_id", "liked_at")],
)
internal data class UserSongEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "song_id")
    val songId: String,
    val name: String? = null,
    val artists: List<String>? = null,
    val thumbnail: Uri? = null,
    @ColumnInfo(name = "liked_at")
    val likedAt: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
