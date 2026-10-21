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
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.UserArtist
import me.knighthat.kreate3.database.model.UserArtistView
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserArtistSort
import me.knighthat.kreate3.model.Origin

/**
 * The artists the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared artist lives in [ArtistRepository].
 */
interface UserArtistRepository {

    /**
     * Every artist in the library, a page at a time. Pages load as the list is read,
     * and the list follows changes in the database.
     *
     * @param origins only the artists from these, every artist when empty
     */
    fun all(
        order: SortOrder<UserArtistSort> = SortOrder(UserArtistSort.NAME),
        origins: Set<Origin> = emptySet(),
    ): Flow<PagingData<UserArtistView>>

    /**
     * Every artist in the library right now, all at once, in the order [all] shows them.
     *
     * @param origins only the artists from these, every artist when empty
     */
    suspend fun list(
        order: SortOrder<UserArtistSort> = SortOrder(UserArtistSort.NAME),
        origins: Set<Origin> = emptySet(),
    ): List<UserArtistView>

    /**
     * How many artists [all] has right now.
     *
     * @param origins only the artists from these, every artist when empty
     */
    suspend fun count(origins: Set<Origin> = emptySet()): Int

    /**
     * The origins the library has artists from. It follows changes in the database.
     */
    fun origins(): Flow<Set<Origin>>

    /**
     * One artist of the library picked at random, `null` when there is none.
     *
     * @param origins only the artists from these, every artist when empty
     */
    suspend fun random(origins: Set<Origin> = emptySet()): UserArtistView?

    /**
     * Artists the user follows, a page at a time. Newest follow first unless [order] says otherwise.
     */
    fun followed(
        order: SortOrder<UserArtistSort> = SortOrder(
            UserArtistSort.FOLLOW_SINCE,
            SortDirection.DESCENDING
        ),
    ): Flow<PagingData<UserArtistView>>

    /**
     * Artists of the library whose name contains [query], a page at a time.
     */
    fun search(
        query: String,
        order: SortOrder<UserArtistSort> = SortOrder(UserArtistSort.NAME),
    ): Flow<PagingData<UserArtistView>>

    fun find(artistId: String): Flow<UserArtistView?>

    /**
     * Adds to the library, or replaces the entry. The [Artist] must be saved first.
     * Use [update] to change a few fields.
     */
    suspend fun save(artist: UserArtist)

    /**
     * Changes what the active user keeps about the artist, only what [transform] changes is replaced.
     * [transform] starts from an empty [UserArtist] when the artist isn't in the library yet, which adds it.
     * The [Artist] must be saved first.
     *
     * @throws IllegalArgumentException when [transform] changes the artist id
     */
    suspend fun update(artistId: String, transform: (UserArtist) -> UserArtist)

    /**
     * Removes the artist from the library. The shared artist stays.
     */
    suspend fun remove(artistId: String)
}
