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

package me.knighthat.kreate.database.repository

import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserAlbum

/**
 * Albums, and the ones the active user keeps in their library.
 *
 * Functions on the shared catalog act for everyone. Functions on the library only touch
 * the active user's data.
 */
interface AlbumRepository {

    fun find(id: String): Flow<Album?>

    /**
     * Songs of the album, in album order.
     */
    fun songsOf(albumId: String): Flow<List<Song>>

    suspend fun save(album: Album)

    /**
     * Deletes the album for everyone, its songs stay without an album.
     * To remove it from one library use [removeFromLibrary].
     */
    suspend fun delete(id: String)

    fun library(): Flow<List<UserAlbum>>

    fun bookmarked(): Flow<List<UserAlbum>>

    fun findInLibrary(albumId: String): Flow<UserAlbum?>

    /**
     * Adds to the library, or updates the entry. The [Album] must be saved first.
     */
    suspend fun saveToLibrary(album: UserAlbum)

    suspend fun removeFromLibrary(albumId: String)
}
