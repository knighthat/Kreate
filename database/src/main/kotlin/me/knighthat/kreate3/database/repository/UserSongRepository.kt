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

import android.net.Uri
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.model.DislikedSong
import me.knighthat.kreate3.database.model.DownloadedSong
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserSongSort
import me.knighthat.kreate3.model.Origin

/**
 * The songs the active user keeps in their library, as the user sees them: the user's own
 * value when set, the shared one otherwise. Only the active user's data is touched,
 * the shared song lives in [SongRepository].
 *
 * Every list leaves out the songs the user disliked, [dislikedIds] are their ids, unless it is
 * asked to keep them ([all]). [find] still gives them, so a dislike can be taken back.
 */
interface UserSongRepository {

    /**
     * Every song in the library, a page at a time. Pages load as the list is read,
     * and the list follows changes in the database.
     *
     * @param origins only the songs from these, every song when empty
     * @param withDisliked keeps the songs the user disliked
     */
    fun all(
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
        origins: Set<Origin> = emptySet(),
        withDisliked: Boolean = false,
    ): Flow<PagingData<UserSongView>>

    /**
     * Every song in the library right now, all at once, in the order [all] shows them.
     *
     * @param origins only the songs from these, every song when empty
     * @param withDisliked keeps the songs the user disliked
     */
    suspend fun list(
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
        origins: Set<Origin> = emptySet(),
        withDisliked: Boolean = false,
    ): List<UserSongView>

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

    /** Every song of [ofAlbum] right now, all at once, in the same order. */
    suspend fun listOfAlbum(
        albumId: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.ALBUM_POSITION),
    ): List<UserSongView>

    /**
     * Songs of the library that the artist is credited on, a page at a time.
     */
    fun ofArtist(
        artistId: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): Flow<PagingData<UserSongView>>

    /** Every song of [ofArtist] right now, all at once, in the same order. */
    suspend fun listOfArtist(
        artistId: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): List<UserSongView>

    /**
     * Songs of the library that are in the playlist, a page at a time, in the playlist's order
     * unless [order] says otherwise.
     */
    fun ofPlaylist(
        playlistId: Long,
        order: SortOrder<UserSongSort>? = null,
    ): Flow<PagingData<UserSongView>>

    /** Every song of [ofPlaylist] right now, all at once, in the same order. */
    suspend fun listOfPlaylist(
        playlistId: Long,
        order: SortOrder<UserSongSort>? = null,
    ): List<UserSongView>

    /**
     * The origins the library has songs from. It follows changes in the database.
     */
    fun origins(): Flow<Set<Origin>>

    /**
     * Ids of the songs the user disliked, of every origin, also songs only seen live: a dislike
     * stores the song. Lists fetched from a source leave these out. It follows changes in the
     * database.
     */
    fun dislikedIds(): Flow<Set<String>>

    /** [dislikedIds] right now, once a user is active. */
    suspend fun listDislikedIds(): Set<String>

    /**
     * The songs the user disliked, a page at a time, most recently disliked first unless
     * [newestFirst] is false. It follows changes in the database.
     *
     * @param origins only the songs from these, every song when empty
     */
    fun disliked(origins: Set<Origin>, newestFirst: Boolean): Flow<PagingData<DislikedSong>>

    /** The origins the disliked songs are from. It follows changes in the database. */
    fun dislikedOrigins(): Flow<Set<Origin>>

    /** Every song of [disliked] right now, all at once. */
    suspend fun listDisliked(origins: Set<Origin>): List<UserSongView>

    /**
     * Songs of the library whose title, artists or album contain [query], a page at a time.
     */
    fun search(
        query: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): Flow<PagingData<UserSongView>>

    /** Every song [search] finds right now, all at once. */
    suspend fun listSearch(
        query: String,
        order: SortOrder<UserSongSort> = SortOrder(UserSongSort.NAME),
    ): List<UserSongView>

    fun find(songId: String): Flow<UserSongView?>

    /**
     * Songs of the library that resemble [songId], most alike first, at most [limit]. Only
     * songs from the same origin as [songId] are considered, the song itself and disliked songs
     * are left out. Each candidate scores a weighted sum of what it shares with [songId]:
     * an artist (3 each), a playlist of the user (2 each), plays close to a play of [songId]
     * (2 each, at most 5 counted), the album (1) and a like (1). Songs that score the same come
     * in random order, so a song that shares nothing still fills the list.
     *
     * @return empty when [songId] isn't known
     */
    suspend fun similar(songId: String, limit: Int): List<UserSongView>

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

    /**
     * Takes [thumbnail] off the songs of every user whose own cover it is, of the song [songId]
     * only, or of any when `null`, like a file deleted or about to be. A row that keeps nothing
     * else about its song is deleted, the song leaves that user's library; any other gets the
     * shared cover back.
     */
    suspend fun forgetThumbnail(thumbnail: Uri, songId: String? = null)

    /** Every cover of a file on the device any user has as their own, see [UserSong.thumbnail]. */
    suspend fun fileThumbnails(): List<Uri>

    /** The songs the active user downloaded, see [UserSong.downloadedAt]. */
    fun downloadedIds(): Flow<Set<String>>

    /**
     * The songs the active user downloaded, whole or not yet, most recently downloaded first
     * unless [newestFirst] is false. Disliked songs are in it, unlike in other lists: the page
     * showing it can show them. It follows changes in the database.
     */
    fun downloaded(newestFirst: Boolean): Flow<List<DownloadedSong>>

    /** The songs any user downloaded. */
    suspend fun downloadedByAnyone(): Set<String>

    /**
     * The song [songId] is no longer downloaded by the active user, or by any user when
     * [everyUser]. Their cover [thumbnail] goes with it. A row that keeps nothing else about the
     * song is deleted, the song leaves that user's library.
     */
    suspend fun forgetDownload(songId: String, thumbnail: Uri?, everyUser: Boolean = false)

    /**
     * Changes what every user who downloaded the song [songId] keeps about it, only what
     * [transform] changes is replaced, like [update] for each of them.
     *
     * @throws IllegalArgumentException when [transform] changes the song id
     */
    suspend fun updateDownloaders(songId: String, transform: (UserSong) -> UserSong)

    /**
     * Moves the song [songId] of the album [albumId] to right after the song [after], to the start
     * when it is `null`, in the active user's album order. The whole order of [listOfAlbum] is
     * kept as the user's then (`UserSong.albumPosition`), a scan that changes the shared positions
     * doesn't change it.
     *
     * @throws IllegalArgumentException when [songId] or [after] isn't in [listOfAlbum]
     */
    suspend fun moveInAlbum(albumId: String, songId: String, after: String?)

    /**
     * Puts the songs of the album [albumId] back in the album's own order for the active user.
     *
     * @return the user's positions it took off, by song id, for [restoreAlbumOrder]
     */
    suspend fun resetAlbumOrder(albumId: String): Map<String, Int>

    /**
     * Gives the songs of the album [albumId] the user's [positions] again, by song id, like
     * [resetAlbumOrder] gave them. Songs no longer in the library are left out.
     */
    suspend fun restoreAlbumOrder(albumId: String, positions: Map<String, Int>)
}
