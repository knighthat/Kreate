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

import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong

/**
 * What a search of the server's library found, each kind in the server's order, best match first.
 *
 * @property songsListed how many songs the server sent, the ones left out without an id too: a
 * page shorter than asked is the last, not one with fewer [songs]. Like [albumsListed] and
 * [artistsListed].
 */
data class OpenSubsonicSearch(
    val songs: List<RemoteSong>,
    val albums: List<RemoteItem<Album>>,
    val artists: List<RemoteItem<Artist>>,
    val songsListed: Int = songs.size,
    val albumsListed: Int = albums.size,
    val artistsListed: Int = artists.size,
)
