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
import me.knighthat.kreate3.model.Origin

/**
 * The cover of one song of a playlist, to show for a playlist that has no picture of its own.
 *
 * @property songId id of the song at its [origin]
 * @property origin where the song comes from
 * @property thumbnail cover of the song as the user's library shows it
 */
data class PlaylistCover(
    val songId: String,
    val origin: Origin,
    val thumbnail: Uri,
)
