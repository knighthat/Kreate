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

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.paging.SongSort
import me.knighthat.kreate.database.paging.SortOrder

/**
 * Albums, and the ones the active user keeps in their library.
 *
 * Everything here acts for all users. What one user keeps about an album lives in [UserAlbumRepository].
 */
interface AlbumRepository {

    fun find(albumId: String): Flow<Album?>

    /**
     * Songs of the album, a page at a time, in album order unless [order] says otherwise.
     */
    fun songsOf(
        albumId: String,
        order: SortOrder<SongSort> = SortOrder(SongSort.ALBUM_POSITION),
    ): Flow<PagingData<Song>>

    suspend fun save(album: Album)

    /**
     * Changes the shared album for everyone, only what [transform] changes is replaced.
     * Use it, not [save], to change a few fields.
     *
     * @throws IllegalArgumentException when the album doesn't exist, or [transform] changes its id
     */
    suspend fun update(albumId: String, transform: (Album) -> Album)

    /**
     * Deletes the album for everyone, its songs stay without an album.
     * To remove it from one library use [UserAlbumRepository.remove].
     */
    suspend fun delete(albumId: String)
}
