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
import androidx.room.Index

/**
 * A [SongEntity] in the queue one user left behind. A song may be queued more than once.
 *
 * @property userId owner of the queue
 * @property ordinal place in the queue, lower plays first
 * @property songId the queued [SongEntity]
 * @property position where the song playing was, in milliseconds. Only that one song of the
 * queue has it, the others are `null`.
 */
@Entity(
    tableName = "persistent_queue",
    primaryKeys = ["user_id", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("song_id")],
)
internal data class PersistentQueueEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    val ordinal: Int,
    @ColumnInfo(name = "song_id")
    val songId: String,
    val position: Long? = null,
)
