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

import android.net.Uri
import kotlin.time.Instant

/**
 * An artist in the active user's library as the user sees it: every value is the user's own
 * when set, the shared one otherwise.
 *
 * @property artistId id of the artist at its [origin]
 * @property origin where the artist comes from
 * @property name name, `null` when unknown
 * @property thumbnail image, `null` for none
 * @property songCount how many stored songs credit the artist
 * @property followSince when the user followed the artist, `null` if not followed
 */
data class UserArtistView(
    val artistId: String,
    val origin: Origin,
    val name: String? = null,
    val thumbnail: Uri? = null,
    val songCount: Int = 0,
    val followSince: Instant? = null,
)
