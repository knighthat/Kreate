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
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.paging.PlaylistSort
import me.knighthat.kreate3.database.paging.SongSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.model.Origin

/**
 * Playlists of the active user. Changing a playlist of another user is rejected.
 */
interface PlaylistRepository {

    /**
     * The playlists of the active user, a page at a time, with their [UserPlaylist.covers]. The
     * list follows plays and changes to the playlists' songs too.
     *
     * @param origins only the playlists from these, every playlist when empty
     * @param monthlyOnly only the monthly playlists
     */
    fun all(
        order: SortOrder<PlaylistSort> = SortOrder(PlaylistSort.NAME),
        origins: Set<Origin> = emptySet(),
        monthlyOnly: Boolean = false,
    ): Flow<PagingData<UserPlaylist>>

    /**
     * Every playlist of the active user right now, all at once, in the order [all] shows them.
     *
     * @param origins only the playlists from these, every playlist when empty
     */
    suspend fun list(
        order: SortOrder<PlaylistSort> = SortOrder(PlaylistSort.NAME),
        origins: Set<Origin> = emptySet(),
    ): List<UserPlaylist>

    /**
     * How many playlists [all] has right now.
     *
     * @param origins only the playlists from these, every playlist when empty
     * @param monthlyOnly only the monthly playlists
     */
    suspend fun count(origins: Set<Origin> = emptySet(), monthlyOnly: Boolean = false): Int

    /**
     * The origins the active user has playlists from. It follows changes in the database.
     */
    fun origins(): Flow<Set<Origin>>

    /**
     * One playlist of the active user picked at random, `null` when there is none.
     *
     * @param origins only the playlists from these, every playlist when empty
     * @param monthlyOnly only the monthly playlists
     */
    suspend fun random(
        origins: Set<Origin> = emptySet(),
        monthlyOnly: Boolean = false,
    ): UserPlaylist?

    /**
     * Playlists of the active user whose name contains [query], a page at a time.
     */
    fun search(
        query: String,
        order: SortOrder<PlaylistSort> = SortOrder(PlaylistSort.NAME),
    ): Flow<PagingData<UserPlaylist>>

    /** Every playlist [search] finds right now, all at once. */
    suspend fun listSearch(
        query: String,
        order: SortOrder<PlaylistSort> = SortOrder(PlaylistSort.NAME),
    ): List<UserPlaylist>

    fun find(playlistId: Long): Flow<UserPlaylist?>

    /**
     * Songs of the playlist, a page at a time.
     */
    fun songsOf(
        playlistId: Long,
        order: SortOrder<SongSort> = SortOrder(SongSort.NAME),
    ): Flow<PagingData<Song>>

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
     * Adds the song at the end of the playlist, or at [position] to put back one [removeSong]
     * took out.
     *
     * @return `false` when the song was in the playlist already, it keeps its place
     * @throws IllegalArgumentException when the playlist doesn't exist or belongs to another user
     */
    suspend fun addSong(playlistId: Long, songId: String, position: Int? = null): Boolean

    /**
     * @return the song's position, for [addSong] to put it back there, `null` when it wasn't in
     * the playlist
     * @throws IllegalArgumentException when the playlist doesn't exist or belongs to another user
     */
    suspend fun removeSong(playlistId: Long, songId: String): Int?

    /**
     * Moves the song to right after [after] in the playlist's order, to the start when [after]
     * is `null`.
     *
     * @throws IllegalArgumentException when the playlist doesn't exist or belongs to another user,
     * or one of the songs isn't in it
     */
    suspend fun moveSong(playlistId: Long, songId: String, after: String?)
}
