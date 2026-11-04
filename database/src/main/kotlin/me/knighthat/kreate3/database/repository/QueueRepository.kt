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
import me.knighthat.kreate3.database.model.QueuedSongDetails

/**
 * The queue the active user left behind, restored on the next start.
 */
interface QueueRepository {

    fun queue(): Flow<List<QueuedSong>>

    /**
     * The queue right now, waiting for a user to be active.
     */
    suspend fun list(): List<QueuedSong>

    /**
     * The queue right now with what each song looks like to the active user, waiting for a user
     * to be active. A queued song the user never kept shows its shared values.
     */
    suspend fun details(): List<QueuedSongDetails>

    /**
     * Swaps the queue for [songIds], in order, a song may appear more than once. The song at
     * [current] was playing at [position], in milliseconds. Songs that aren't saved are left out.
     */
    suspend fun replace(songIds: List<String>, current: Int, position: Long)

    /**
     * Marks the song at [index] of the queue last saved with [replace] as playing at [position],
     * in milliseconds. The song that had a position loses it, so only one ever has one.
     */
    suspend fun setPosition(index: Int, position: Long)

    /**
     * Puts [songIds] in the queue last saved, starting at [index], and pushes the songs after
     * them back. Only those songs are written. Songs that aren't saved are left out.
     */
    suspend fun insert(index: Int, songIds: List<String>)

    /** Takes [count] songs from [index] on out of the queue last saved, the songs after them close up. */
    suspend fun remove(index: Int, count: Int)

    /**
     * Moves [count] songs from [index] on so that they start at [to] of the queue left without
     * them, like `Player.moveMediaItems`. Only the places in between change.
     */
    suspend fun move(index: Int, count: Int, to: Int)

    suspend fun clear()
}
