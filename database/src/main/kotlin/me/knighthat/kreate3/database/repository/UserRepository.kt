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
import me.knighthat.kreate3.database.model.User

/**
 * Profiles stored in the database.
 */
interface UserRepository {

    fun all(): Flow<List<User>>

    fun find(id: String): Flow<User?>

    /**
     * Adds the user, or updates the one with the same id.
     */
    suspend fun save(user: User)

    /**
     * Changes the display name of the user. Does nothing for an unknown user.
     *
     * @throws IllegalArgumentException for a blank [name]
     */
    suspend fun rename(id: String, name: String)

    /**
     * Records that the user became the active one just now. Does nothing for an unknown user.
     */
    suspend fun markActive(id: String)

    /**
     * Records that the user was active until just now. Does nothing for an unknown user.
     */
    suspend fun markSeen(id: String)

    /**
     * Removes the user and everything the user owns: library, playlists, history and queue.
     * The shared catalog stays, other users may still use it.
     */
    suspend fun delete(id: String)
}
