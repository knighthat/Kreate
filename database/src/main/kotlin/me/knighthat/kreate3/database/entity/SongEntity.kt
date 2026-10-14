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
import androidx.room.PrimaryKey
import me.knighthat.kreate3.database.model.Origin
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Song shared by every user. Per-user overrides live in [UserSongEntity].
 *
 * Deleting the album keeps the song and clears [albumId].
 *
 * @property id id of the song at its [origin]
 * @property origin where the song comes from
 * @property name title of the song, `null` when unknown
 * @property thumbnail cover of the song, `null` to use the one of its album
 * @property duration length of the song, whole seconds in storage
 * @property isExplicit `null` when unknown
 * @property albumId album the song belongs to, `null` for none
 * @property albumPosition position of the song in its album
 * @property unavailableSince when a scan first missed the song, `null` while it is available
 * @property createdAt when the song was first stored
 * @property updatedAt when the song was last changed
 */
@Entity(
    tableName = "song",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("album_id", "album_position")],
)
internal data class SongEntity(
    @PrimaryKey
    val id: String,
    val origin: Origin,
    val name: String? = null,
    val thumbnail: Uri? = null,
    val duration: Duration? = null,
    @ColumnInfo(name = "is_explicit")
    val isExplicit: Boolean? = null,
    @ColumnInfo(name = "album_id")
    val albumId: String? = null,
    @ColumnInfo(name = "album_position")
    val albumPosition: Int? = null,
    @ColumnInfo(name = "unavailable_since")
    val unavailableSince: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
