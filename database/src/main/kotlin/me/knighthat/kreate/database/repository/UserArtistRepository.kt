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
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.UserArtist
import me.knighthat.kreate.database.model.UserArtistView

/**
 * The artists the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared artist lives in [ArtistRepository].
 */
interface UserArtistRepository {

    fun all(): Flow<List<UserArtistView>>

    fun followed(): Flow<List<UserArtistView>>

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
