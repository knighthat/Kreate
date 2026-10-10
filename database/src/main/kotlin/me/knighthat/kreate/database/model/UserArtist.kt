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

package me.knighthat.kreate.database.model

import kotlin.time.Instant

/**
 * What the active user keeps about an [Artist]. [name] and [thumbnail] override the shared values.
 *
 * @property artistId the [Artist] this is about
 * @property name name preferred by the user, `null` to use the shared one
 * @property thumbnail image preferred by the user, `null` to use the shared one
 * @property followSince when the user followed the artist, `null` if not followed
 */
data class UserArtist(
    val artistId: String,
    val name: String? = null,
    val thumbnail: String? = null,
    val followSince: Instant? = null,
)
