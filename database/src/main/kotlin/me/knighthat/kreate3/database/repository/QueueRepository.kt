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
import me.knighthat.kreate3.database.model.QueuedSong

/**
 * The queue the active user left behind, restored on the next start.
 */
interface QueueRepository {

    fun queue(): Flow<List<QueuedSong>>

    /**
     * Swaps the queue for [songIds], in order. A song appears once, the songs must be saved first.
     */
    suspend fun replace(songIds: List<String>)

    suspend fun clear()
}
