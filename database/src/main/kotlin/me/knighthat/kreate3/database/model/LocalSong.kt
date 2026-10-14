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
import kotlin.time.Duration

/**
 * A song found on the device. Ids are the ones of the device's media index.
 *
 * @property id id of the song in the media index
 * @property name title, `null` when unknown
 * @property thumbnail cover of the album the song is in, `null` when it has no album. A cover in the
 *   file itself is read from the file and takes priority over this one.
 * @property duration length of the song
 * @property albumPosition position of the song in its album
 * @property artist the artist of the song, `null` when unknown
 * @property album the album of the song, `null` when unknown
 */
data class LocalSong(
    val id: String,
    val name: String? = null,
    val thumbnail: Uri? = null,
    val duration: Duration? = null,
    val albumPosition: Int? = null,
    val artist: LocalArtist? = null,
    val album: LocalAlbum? = null,
)

/**
 * @property id id of the artist in the media index
 * @property name name of the artist, `null` when unknown
 */
data class LocalArtist(
    val id: String,
    val name: String? = null,
)

/**
 * @property id id of the album in the media index
 * @property name title of the album, `null` when unknown
 * @property year release year, `null` when unknown
 */
data class LocalAlbum(
    val id: String,
    val name: String? = null,
    val year: Int? = null,
)
