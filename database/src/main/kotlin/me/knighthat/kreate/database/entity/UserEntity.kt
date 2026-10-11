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
import kotlin.time.Instant

/**
 * A profile. [id] is the profile id handed out by `ProfileManager`.
 *
 * Deleting a row removes everything the user owns (`user_*` tables, history and queue).
 *
 * @property id same id as the profile in `ProfileManager`
 * @property name display name of the profile
 * @property thumbnail image of the profile, `null` for none
 * @property activeSince when the profile last became the active one, `null` if it never was
 * @property lastSeen when the profile was last active, `null` if it never was
 * @property createdAt when the profile was first stored
 * @property updatedAt when the profile was last changed
 */
@Entity(tableName = "user")
internal data class UserEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val thumbnail: Uri? = null,
    @ColumnInfo(name = "active_since")
    val activeSince: Instant? = null,
    @ColumnInfo(name = "last_seen")
    val lastSeen: Instant? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
