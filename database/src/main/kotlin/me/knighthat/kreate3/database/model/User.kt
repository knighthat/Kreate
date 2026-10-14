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

package me.knighthat.kreate3.database.model

import android.net.Uri
import kotlin.time.Instant

/**
 * A profile.
 *
 * @property id same id as the profile in `ProfileManager`
 * @property name display name of the profile
 * @property thumbnail image of the profile, `null` for none
 * @property activeSince when the profile last became the active one, `null` if it never was
 * @property lastSeen when the profile was last active, `null` if it never was
 */
data class User(
    val id: String,
    val name: String,
    val thumbnail: Uri? = null,
    val activeSince: Instant? = null,
    val lastSeen: Instant? = null,
)
