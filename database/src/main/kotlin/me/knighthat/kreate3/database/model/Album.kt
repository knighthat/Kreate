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

/**
 * Album shared by every user. Per-user overrides live in [UserAlbum].
 *
 * @property id id of the album at its [origin]
 * @property origin where the album comes from
 * @property name title of the album, `null` when unknown
 * @property thumbnail cover of the album, `null` for none
 * @property year release year, `null` when unknown
 * @property artists artist names as displayed by the source
 * @property url page of the album at its [origin]
 * @property isExplicit `null` when unknown
 */
data class Album(
    val id: String,
    val origin: Origin,
    val name: String? = null,
    val thumbnail: Uri? = null,
    val year: Int? = null,
    val artists: List<String>? = null,
    val url: String? = null,
    val isExplicit: Boolean? = null,
)
