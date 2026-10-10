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
import me.knighthat.kreate.database.model.User

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
     * Removes the user and everything the user owns: library, playlists, history and queue.
     * The shared catalog stays, other users may still use it.
     */
    suspend fun delete(id: String)
}
