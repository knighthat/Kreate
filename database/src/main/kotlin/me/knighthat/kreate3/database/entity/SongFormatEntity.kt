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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import kotlin.time.Instant

/**
 * One stream format of a [SongEntity].
 *
 * @property songId the [SongEntity] this format belongs to
 * @property tag identifier of the format at the source
 * @property mimetype media type of the stream
 * @property bitrate bits per second
 * @property length size in bytes
 * @property gain loudness of the source relative to the reference in dB, positive when louder than the reference;
 *   OpenSubsonic's `raw` format holds the server's ReplayGain as given instead (negative when louder), its
 *   reference is a setting
 * @property createdAt when the format was first stored
 * @property updatedAt when the format was last changed
 */
@Entity(
    tableName = "song_format",
    primaryKeys = ["song_id", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class SongFormatEntity(
    @ColumnInfo(name = "song_id")
    val songId: String,
    val tag: String,
    val mimetype: String,
    val bitrate: Int? = null,
    val length: Long? = null,
    val gain: Float? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
