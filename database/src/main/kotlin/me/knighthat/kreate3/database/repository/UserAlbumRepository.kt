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

package me.knighthat.kreate3.database.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.UserAlbum
import me.knighthat.kreate3.database.model.UserAlbumView
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserAlbumSort

/**
 * The albums the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared album lives in [AlbumRepository].
 */
interface UserAlbumRepository {

    /**
     * Every album in the library, a page at a time. Pages load as the list is read,
     * and the list follows changes in the database.
     *
     * @param origins only the albums from these, every album when empty
     */
    fun all(
        order: SortOrder<UserAlbumSort> = SortOrder(UserAlbumSort.NAME),
        origins: Set<Origin> = emptySet(),
    ): Flow<PagingData<UserAlbumView>>

    /**
     * How many albums [all] has right now.
     *
     * @param origins only the albums from these, every album when empty
     */
    suspend fun count(origins: Set<Origin> = emptySet()): Int

    /**
     * The origins the library has albums from. It follows changes in the database.
     */
    fun origins(): Flow<Set<Origin>>

    /**
     * One album of the library picked at random, `null` when there is none.
     *
     * @param origins only the albums from these, every album when empty
     */
    suspend fun random(origins: Set<Origin> = emptySet()): UserAlbumView?

    /**
     * Albums the user bookmarked, a page at a time. Newest bookmark first unless [order] says otherwise.
     */
    fun bookmarked(
        order: SortOrder<UserAlbumSort> = SortOrder(
            UserAlbumSort.BOOKMARKED_AT,
            SortDirection.DESCENDING
        ),
    ): Flow<PagingData<UserAlbumView>>

    /**
     * Albums of the library that have a song the artist is credited on, newest first, at most
     * [limit] of them. The list follows changes in the database.
     */
    fun ofArtist(artistId: String, limit: Int): Flow<List<UserAlbumView>>

    /**
     * Albums of the library whose title or artists contain [query], a page at a time.
     */
    fun search(
        query: String,
        order: SortOrder<UserAlbumSort> = SortOrder(UserAlbumSort.NAME),
    ): Flow<PagingData<UserAlbumView>>

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
