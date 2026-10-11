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

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Song shared by every user. Per-user overrides live in [UserSong].
 *
 * @property id id of the song at its [origin]
 * @property origin where the song comes from
 * @property name title of the song, `null` when unknown
 * @property thumbnail cover of the song, `null` to use the one of its album
 * @property duration length of the song, accurate to the second
 * @property isExplicit `null` when unknown
 * @property albumId album the song belongs to, `null` for none
 * @property albumPosition position of the song in its album
 * @property unavailableSince when a scan first missed the song, `null` while it is available
 */
data class Song(
    val id: String,
    val origin: Origin,
    val name: String? = null,
    val thumbnail: String? = null,
    val duration: Duration? = null,
    val isExplicit: Boolean? = null,
    val albumId: String? = null,
    val albumPosition: Int? = null,
    val unavailableSince: Instant? = null,
)
