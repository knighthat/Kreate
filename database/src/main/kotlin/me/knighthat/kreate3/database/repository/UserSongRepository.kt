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
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserSongSort

/**
 * The songs the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared song lives in [SongRepository].
 */
interface UserSongRepository {

    /**
     * Every song in the library, a page at a time. Pages load as the list is read,
     * and the list follows changes in the database.
     */
    fun all(order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME)): Flow<PagingData<UserSongView>>

    /**
     * Songs the user liked, a page at a time. Newest like first unless [order] says otherwise.
     */
    fun liked(
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.LIKED_AT, SortDirection.DESCENDING),
    ): Flow<PagingData<UserSongView>>

    /**
     * Songs of the library that are on the album, a page at a time, in album order unless
     * [order] says otherwise.
     */
    fun ofAlbum(
        albumId: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.ALBUM_POSITION),
    ): Flow<PagingData<UserSongView>>

    /**
     * Songs of the library that the artist is credited on, a page at a time.
     */
    fun ofArtist(
        artistId: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): Flow<PagingData<UserSongView>>

    /**
     * Songs of the library that are in the playlist, a page at a time.
     */
    fun ofPlaylist(
        playlistId: Long,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): Flow<PagingData<UserSongView>>

    /**
     * Songs of the library whose title, artists or album contain [query], a page at a time.
     */
    fun search(
        query: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): Flow<PagingData<UserSongView>>

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
