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

import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.model.Artist

/**
 * Artists, and the ones the active user keeps in their library.
 *
 * Everything here acts for all users. What one user keeps about a artist lives in [UserArtistRepository].
 */
interface ArtistRepository {

    fun find(artistId: String): Flow<Artist?>

    suspend fun save(artist: Artist)

    /**
     * Changes the shared artist for everyone, only what [transform] changes is replaced.
     * Use it, not [save], to change a few fields.
     *
     * @throws IllegalArgumentException when the artist doesn't exist, or [transform] changes its id
     */
    suspend fun update(artistId: String, transform: (Artist) -> Artist)

    /**
     * Deletes the artist for everyone. To remove it from one library use [UserArtistRepository.remove].
     */
    suspend fun delete(artistId: String)
}
