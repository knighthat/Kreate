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
import androidx.room.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.UserSongDao
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserSongSort
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import me.knighthat.kreate3.database.view.UserSongViewEntity
import me.knighthat.kreate3.model.Origin
import kotlin.time.Clock

/**
 * Room backed implementation of [UserSongRepository].
 */
internal class UserSongRepositoryImpl(
    private val database: AppDatabase,
    private val userSongDao: UserSongDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : UserSongRepository {

    private val scope = UserScope(activeUser)

    override fun all(
        order: SortOrder<UserSongSort>,
        origins: Set<Origin>,
    ): Flow<PagingData<UserSongView>> = paged(order.toOrderBy(), filter = originFilter(origins))

    override suspend fun list(order: SortOrder<UserSongSort>, origins: Set<Origin>) =
        listed(order.toOrderBy(), filter = originFilter(origins))

    // A like is a time, -1 is a dislike
    override fun liked(order: SortOrder<UserSongSort>): Flow<PagingData<UserSongView>> =
        paged(order.toOrderBy(), filter = "AND liked_at >= 0")

    override fun search(query: String, order: SortOrder<UserSongSort>) =
        paged(
            order.toOrderBy(),
            filter = "AND (name LIKE ?2 ESCAPE '\\' OR artists LIKE ?2 ESCAPE '\\' " +
                    "OR album_name LIKE ?2 ESCAPE '\\')",
        ) { it.bindText(2, containsPattern(query)) }

    override fun ofAlbum(albumId: String, order: SortOrder<UserSongSort>) =
        paged(order.toOrderBy(), filter = OF_ALBUM) { it.bindText(2, albumId) }

    override suspend fun listOfAlbum(albumId: String, order: SortOrder<UserSongSort>) =
        listed(order.toOrderBy(), filter = OF_ALBUM) { it.bindText(2, albumId) }

    override fun ofArtist(artistId: String, order: SortOrder<UserSongSort>) =
        paged(order.toOrderBy(), filter = OF_ARTIST) { it.bindText(2, artistId) }

    override suspend fun listOfArtist(artistId: String, order: SortOrder<UserSongSort>) =
        listed(order.toOrderBy(), filter = OF_ARTIST) { it.bindText(2, artistId) }

    // The playlist's songs and their order are in song_playlist_map, the view doesn't read it
    override fun ofPlaylist(playlistId: Long, order: SortOrder<UserSongSort>?) =
        paged(
            order?.toOrderBy() ?: PLAYLIST_ORDER,
            filter = OF_PLAYLIST,
            tables = arrayOf("user_song_view", "song_playlist_map"),
        ) { it.bindLong(2, playlistId) }

    override suspend fun listOfPlaylist(playlistId: Long, order: SortOrder<UserSongSort>?) =
        listed(order?.toOrderBy() ?: PLAYLIST_ORDER, filter = OF_PLAYLIST) {
            it.bindLong(2, playlistId)
        }

    override fun origins(): Flow<Set<Origin>> =
        scope.observe(emptySet()) { userId -> userSongDao.origins(userId).map { it.toSet() } }

    override fun dislikedIds(): Flow<Set<String>> =
        scope.observe(emptySet()) { userId ->
            userSongDao.dislikedIds(userId).map { it.toSet() }
        }.distinctUntilChanged()

    override suspend fun listDislikedIds(): Set<String> =
        userSongDao.listDislikedIds(scope.awaitId()).toSet()

    /**
     * The user's songs that [filter] keeps, disliked ones are always left out.
     *
     * @param orderBy the `ORDER BY` clause with a unique column last, it can use the values of
     * [filter]
     * @param filter extra conditions after the user's, its `?` start at position 2
     * @param tables what the query reads, a change in them loads the list again
     * @param bind binds the values of [filter]
     */
    private fun paged(
        orderBy: String,
        filter: String,
        tables: Array<String> = arrayOf("user_song_view"),
        bind: (SQLiteStatement) -> Unit = {},
    ): Flow<PagingData<UserSongView>> =
        scope.observe(PagingData.empty()) { userId ->
            database.pagedFlow(UserSongViewEntity::toModel, *tables) {
                userSongDao.paged(query(userId, orderBy, filter, bind))
            }
        }

    /** Like [paged], every song at once. */
    private suspend fun listed(
        orderBy: String,
        filter: String,
        bind: (SQLiteStatement) -> Unit = {},
    ): List<UserSongView> =
        userSongDao.list(query(scope.awaitId(), orderBy, filter, bind))
            .map(UserSongViewEntity::toModel)

    /** @param includeDisliked keeps the disliked songs, which no list shows */
    private fun query(
        userId: String,
        orderBy: String,
        filter: String,
        bind: (SQLiteStatement) -> Unit,
        includeDisliked: Boolean = false,
    ) = RoomRawQuery(
        sql = "SELECT * FROM user_song_view WHERE user_id = ? " +
                "${if (includeDisliked) "" else NOT_DISLIKED} $filter $orderBy",
        onBindStatement = {
            it.bindText(1, userId)
            bind(it)
        },
    )

    override fun find(songId: String): Flow<UserSongView?> =
        scope.observe(null) { userId ->
            userSongDao.find(userId, songId).map { it?.toModel() }
        }

    override suspend fun similar(songId: String, limit: Int): List<UserSongView> {
        val userId = scope.awaitId()
        return userSongDao.list(
            RoomRawQuery(
                sql = SIMILAR,
                onBindStatement = {
                    it.bindText(1, userId)
                    it.bindText(2, songId)
                    it.bindLong(3, limit.toLong())
                },
            )
        ).map(UserSongViewEntity::toModel)
    }

    override suspend fun save(song: UserSong) {
        val userId = scope.requireId()
        val now = clock.now()
        val createdAt = userSongDao.get(userId, song.songId)?.createdAt ?: now
        userSongDao.upsert(song.toEntity(userId, createdAt, now))
    }

    override suspend fun update(songId: String, transform: (UserSong) -> UserSong) {
        val userId = scope.requireId()
        database.inTransaction {
            val current = userSongDao.get(userId, songId)
            val updated = transform(current?.toModel() ?: UserSong(songId))
            require(updated.songId == songId) { "The song of a library entry cannot be changed" }

            val now = clock.now()
            userSongDao.upsert(updated.toEntity(userId, current?.createdAt ?: now, now))
        }
    }

    override suspend fun remove(songId: String) =
        userSongDao.delete(scope.requireId(), songId)

    override suspend fun forgetThumbnail(thumbnail: Uri, songId: String?) =
        database.inTransaction {
            userSongDao.deleteKeepingOnly(thumbnail, songId)
            userSongDao.clearThumbnail(thumbnail, songId, clock.now())
        }

    override suspend fun fileThumbnails(): List<Uri> = userSongDao.fileThumbnails()

    override fun downloadedIds(): Flow<Set<String>> =
        scope.observe(emptySet()) { userId ->
            userSongDao.downloadedIds(userId).map { it.toSet() }
        }.distinctUntilChanged()

    override suspend fun downloadedByAnyone(): Set<String> =
        userSongDao.downloadedByAnyone().toSet()

    override suspend fun forgetDownload(songId: String, thumbnail: Uri?, everyUser: Boolean) {
        val userId = if (everyUser) null else scope.requireId()
        database.inTransaction {
            userSongDao.deleteKeepingOnlyDownload(songId, userId, thumbnail)
            userSongDao.clearDownload(songId, userId, thumbnail, clock.now())
        }
    }

    override suspend fun updateDownloaders(songId: String, transform: (UserSong) -> UserSong) =
        database.inTransaction {
            val now = clock.now()
            userSongDao.downloaders(songId).forEach { current ->
                val updated = transform(current.toModel())
                require(updated.songId == songId) { "The song of a library entry cannot be changed" }
                userSongDao.upsert(updated.toEntity(current.userId, current.createdAt, now))
            }
        }

    override suspend fun moveInAlbum(albumId: String, songId: String, after: String?) {
        val userId = scope.requireId()
        database.inTransaction {
            // Ids, not indices: the view leaves out songs that aren't in the library. Hidden
            // disliked songs are numbered too, they keep their place for when they come back
            val order = userSongDao.list(
                query(
                    userId,
                    SortOrder(UserSongSort.ALBUM_POSITION).toOrderBy(),
                    OF_ALBUM,
                    bind = { it.bindText(2, albumId) },
                    includeDisliked = true,
                )
            ).map { it.songId }.toMutableList()
            val from = order.indexOf(songId)
            require(from >= 0) { "Song $songId isn't on album $albumId" }
            order.removeAt(from)
            val to = if (after == null) 0 else order.indexOf(after) + 1
            require(to > 0 || after == null) { "Song $after isn't on album $albumId" }
            order.add(to, songId)

            // Every song gets the user's own position, so the shared ones no longer mix in
            val rows = userSongDao.onAlbum(userId, albumId).associateBy { it.songId }
            val now = clock.now()
            val changed = order.mapIndexedNotNull { index, id ->
                rows[id]?.takeIf { it.albumPosition != index }
                    ?.copy(albumPosition = index, updatedAt = now)
            }
            userSongDao.upsertAll(changed)
        }
    }

    override suspend fun resetAlbumOrder(albumId: String) =
        userSongDao.clearAlbumPositions(scope.requireId(), albumId, clock.now())

    private companion object {

        /** Plays this close to each other, in milliseconds, were part of one listening session. */
        const val SESSION_MS = 30 * 60 * 1000

        // ?1 user, ?2 seed song, ?3 limit
        const val SIMILAR = """
            SELECT v.* FROM user_song_view v
            WHERE v.user_id = ?1
              AND v.song_id != ?2
              AND v.origin = (SELECT origin FROM song WHERE id = ?2)
              AND (v.liked_at IS NULL OR v.liked_at >= 0)
            ORDER BY (
                3 * (SELECT COUNT(*) FROM song_artist_map a
                     JOIN song_artist_map b ON b.artist_id = a.artist_id
                     WHERE a.song_id = ?2 AND b.song_id = v.song_id)
                + 2 * (SELECT COUNT(*) FROM song_playlist_map a
                       JOIN song_playlist_map b ON b.playlist_id = a.playlist_id
                       JOIN user_playlist p ON p.playlist_id = a.playlist_id
                       WHERE a.song_id = ?2 AND b.song_id = v.song_id AND p.user_id = ?1)
                + 2 * MIN(5, (SELECT COUNT(*) FROM playback_history s
                              JOIN playback_history h ON h.user_id = s.user_id
                                  AND h.song_id = v.song_id
                                  AND h.created_at BETWEEN s.created_at - $SESSION_MS
                                                       AND s.created_at + $SESSION_MS
                              WHERE s.user_id = ?1 AND s.song_id = ?2))
                + CASE WHEN v.album_id IS NOT NULL
                        AND v.album_id = (SELECT album_id FROM song WHERE id = ?2)
                       THEN 1 ELSE 0 END
                + CASE WHEN v.liked_at >= 0 THEN 1 ELSE 0 END
            ) DESC, RANDOM()
            LIMIT ?3
        """

        // A like is a time, -1 is a dislike
        const val NOT_DISLIKED = "AND (liked_at IS NULL OR liked_at >= 0)"

        const val OF_ALBUM = "AND album_id = ?"
        const val OF_ARTIST =
            "AND song_id IN (SELECT song_id FROM song_artist_map WHERE artist_id = ?)"
        const val OF_PLAYLIST =
            "AND song_id IN (SELECT song_id FROM song_playlist_map WHERE playlist_id = ?)"

        // ?2 is the playlist of OF_PLAYLIST
        const val PLAYLIST_ORDER =
            "ORDER BY (SELECT position FROM song_playlist_map m " +
                    "WHERE m.playlist_id = ?2 AND m.song_id = user_song_view.song_id), song_id"

        fun SortOrder<UserSongSort>.toOrderBy() = toOrderBy("song_id")
    }
}
