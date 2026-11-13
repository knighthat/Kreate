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
 * A stored song with the values its source gave, none of a user's overrides: not the user's
 * title, artists or picture, nor the names a user gave its artists.
 *
 * @property artists the credited artists, in credit order
 * @property albumName the shared name of its album, `null` for none or no album
 * @property albumYear its album's release year, `null` when unknown
 * @property albumThumbnail the shared cover of its album, `null` for none
 */
data class SourceSong(
    val song: Song,
    val artists: List<SourceArtist>,
    val albumName: String? = null,
    val albumYear: Int? = null,
    val albumThumbnail: Uri? = null,
) {

    /** The song's own cover, else its album's. */
    val thumbnail: Uri? get() = song.thumbnail ?: albumThumbnail
}

/**
 * An artist credited on a [SourceSong].
 *
 * @property name the artist's shared name, `null` when the source gave none
 */
data class SourceArtist(val id: String, val name: String?)
