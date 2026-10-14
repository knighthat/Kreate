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
import kotlin.time.Instant

/**
 * A search one user made.
 *
 * @property query the text that was searched
 * @property userId owner of this entry
 * @property lastUsed when the user last searched for [query]
 */
@Entity(
    tableName = "search_history",
    primaryKeys = ["query", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("user_id", "last_used")],
)
internal data class SearchHistoryEntity(
    val query: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "last_used")
    val lastUsed: Instant? = null,
)
