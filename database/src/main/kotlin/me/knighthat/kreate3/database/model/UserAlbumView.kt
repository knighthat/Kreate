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
 * An album in the active user's library as the user sees it: every value is the user's own
 * when set, the shared one otherwise.
 *
 * @property albumId id of the album at its [origin]
 * @property origin where the album comes from
 * @property name title, `null` when unknown
 * @property artists artist names, the user's own list when set
 * @property thumbnail cover, `null` for none
 * @property year release year, `null` when unknown
 * @property url page of the album at its [origin]
 * @property isExplicit `null` when unknown
 * @property songCount how many stored songs belong to the album
 * @property bookmarkedAt when the user bookmarked the album, `null` if not bookmarked
 */
data class UserAlbumView(
    val albumId: String,
    val origin: Origin,
    val name: String? = null,
    val artists: List<String> = emptyList(),
    val thumbnail: Uri? = null,
    val year: Int? = null,
    val url: String? = null,
    val isExplicit: Boolean? = null,
    val songCount: Int = 0,
    val bookmarkedAt: Instant? = null,
)
