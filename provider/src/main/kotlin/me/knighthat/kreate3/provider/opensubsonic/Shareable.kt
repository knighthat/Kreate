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

/**
 * What can be shared from the server, by its id there.
 */
sealed interface Shareable {

    val id: String

    data class Song(override val id: String) : Shareable

    data class Album(override val id: String) : Shareable

    /** Shared as all of the artist's albums. */
    data class Artist(override val id: String) : Shareable

    data class Playlist(override val id: String) : Shareable
}
