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
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserSong
import me.knighthat.kreate.database.model.UserSongView

/**
 * The songs the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared song lives in [SongRepository].
 */
interface UserSongRepository {

    fun all(): Flow<List<UserSongView>>

    fun liked(): Flow<List<UserSongView>>

    fun find(songId: String): Flow<UserSongView?>

    /**
     * Adds to the library, or replaces the entry. The [Song] must be saved first.
     * Use [update] to change a few fields.
     */
    suspend fun save(song: UserSong)

    /**
     * Changes what the active user keeps about the song, only what [transform] changes is replaced.
     * [transform] starts from an empty [UserSong] when the song isn't in the library yet, which adds it.
     * The [Song] must be saved first.
     *
     * @throws IllegalArgumentException when [transform] changes the song id
     */
    suspend fun update(songId: String, transform: (UserSong) -> UserSong)

    /**
     * Removes the song from the library. The shared song stays.
     */
    suspend fun remove(songId: String)
}
