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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate.database.ActiveUser

/**
 * Ties a query to the active user. The query restarts when the user changes.
 */
internal class UserScope(private val activeUser: ActiveUser) {

    /**
     * Runs [query] for the active user, emits [whenNone] while no user is active.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(whenNone: T, query: (userId: String) -> Flow<T>): Flow<T> =
        activeUser.id.flatMapLatest { if (it == null) flowOf(whenNone) else query(it) }

    /**
     * Suspends until a user is active, then returns its id.
     */
    suspend fun awaitId(): String = activeUser.id.filterNotNull().first()

    /**
     * Id of the active user right now. Call it first in a write, so the data lands
     * on the user who started it even if the user changes meanwhile.
     *
     * @throws IllegalStateException when no user is active
     */
    fun requireId(): String = checkNotNull(activeUser.id.value) { "No active user" }
}
