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

package me.knighthat.kreate3.provider.opensubsonic

import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteSong

/**
 * A playlist of the server with its songs, in its order.
 *
 * @property editable whether the user may change or delete it: the server says so, or else it
 * is the user's own. A playlist someone else shared is not.
 */
data class ServerPlaylist(
    val playlist: UserPlaylist,
    val songs: List<RemoteSong>,
    val editable: Boolean,
)

/**
 * A playlist just made on the server.
 *
 * @property id id on the server, `null` when the server didn't tell it
 * @property optionsSaved whether the comment and public flag were sent too, they need a second
 * request after the playlist exists
 */
data class CreatedPlaylist(val id: String?, val optionsSaved: Boolean)
