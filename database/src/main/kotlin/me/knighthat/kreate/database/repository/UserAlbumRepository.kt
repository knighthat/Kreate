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
import me.knighthat.kreate.database.model.UserAlbum
import me.knighthat.kreate.database.model.UserAlbumView

/**
 * The albums the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared album lives in [AlbumRepository].
 */
interface UserAlbumRepository {

    fun all(): Flow<List<UserAlbumView>>

    fun bookmarked(): Flow<List<UserAlbumView>>

    fun find(albumId: String): Flow<UserAlbumView?>

    /**
     * Adds to the library, or replaces the entry. The [Album] must be saved first.
     * Use [update] to change a few fields.
     */
    suspend fun save(album: UserAlbum)

    /**
     * Changes what the active user keeps about the album, only what [transform] changes is replaced.
     * [transform] starts from an empty [UserAlbum] when the album isn't in the library yet, which adds it.
     * The [Album] must be saved first.
     *
     * @throws IllegalArgumentException when [transform] changes the album id
     */
    suspend fun update(albumId: String, transform: (UserAlbum) -> UserAlbum)

    /**
     * Removes the album from the library. The shared album stays.
     */
    suspend fun remove(albumId: String)
}
