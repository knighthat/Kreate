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
 * A playlist of the active user.
 *
 * @property id id of the playlist, `0` for a playlist that is not saved yet
 * @property origin where the playlist comes from
 * @property name name chosen by the user, a playlist always has one
 * @property thumbnail image of the playlist, `null` for none
 * @property isMonthly whether this is a monthly playlist
 * @property remoteId id of the playlist at its [origin], `null` for one made in the app
 */
data class UserPlaylist(
    val id: Long = 0,
    val origin: Origin,
    val name: String,
    val thumbnail: Uri? = null,
    val isMonthly: Boolean = false,
    val remoteId: String? = null,
)
