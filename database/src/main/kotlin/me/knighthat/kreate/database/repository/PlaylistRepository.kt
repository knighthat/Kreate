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
import me.knighthat.kreate.database.model.UserPlaylist

/**
 * Playlists of the active user. Changing a playlist of another user is rejected.
 */
interface PlaylistRepository {

    fun all(): Flow<List<UserPlaylist>>

    fun find(playlistId: Long): Flow<UserPlaylist?>

    fun songsOf(playlistId: Long): Flow<List<Song>>

    /**
     * Creates the playlist when its id is `0`, otherwise updates it.
     *
     * @return the id of the playlist
     * @throws IllegalArgumentException when the playlist doesn't exist or belongs to another user
     */
    suspend fun save(playlist: UserPlaylist): Long

    /**
     * Deletes the playlist and its song links. Does nothing for a playlist of another user.
     */
    suspend fun delete(playlistId: Long)

    /**
     * @throws IllegalArgumentException when the playlist doesn't exist or belongs to another user
     */
    suspend fun addSong(playlistId: Long, songId: String)

    /**
     * @throws IllegalArgumentException when the playlist doesn't exist or belongs to another user
     */
    suspend fun removeSong(playlistId: Long, songId: String)
}
