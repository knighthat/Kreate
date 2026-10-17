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
import androidx.room.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.UserSongDao
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserSongSort
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import me.knighthat.kreate3.database.view.UserSongViewEntity
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
    ): Flow<PagingData<UserSongView>> = paged(order, filter = originFilter(origins))

    override suspend fun list(order: SortOrder<UserSongSort>, origins: Set<Origin>) =
        listed(order, filter = originFilter(origins))

    // A like is a time, -1 is a dislike
    override fun liked(order: SortOrder<UserSongSort>): Flow<PagingData<UserSongView>> =
        paged(order, filter = "AND liked_at >= 0")

    override fun search(query: String, order: SortOrder<UserSongSort>) =
        paged(
            order,
            filter = "AND (name LIKE ?2 ESCAPE '\\' OR artists LIKE ?2 ESCAPE '\\' " +
                    "OR album_name LIKE ?2 ESCAPE '\\')",
        ) { it.bindText(2, containsPattern(query)) }

    override fun ofAlbum(albumId: String, order: SortOrder<UserSongSort>) =
        paged(order, filter = OF_ALBUM) { it.bindText(2, albumId) }

    override suspend fun listOfAlbum(albumId: String, order: SortOrder<UserSongSort>) =
        listed(order, filter = OF_ALBUM) { it.bindText(2, albumId) }

    override fun ofArtist(artistId: String, order: SortOrder<UserSongSort>) =
        paged(order, filter = OF_ARTIST) { it.bindText(2, artistId) }

    override suspend fun listOfArtist(artistId: String, order: SortOrder<UserSongSort>) =
        listed(order, filter = OF_ARTIST) { it.bindText(2, artistId) }

    override fun ofPlaylist(playlistId: Long, order: SortOrder<UserSongSort>) =
        paged(order, filter = OF_PLAYLIST) { it.bindLong(2, playlistId) }

    override suspend fun listOfPlaylist(playlistId: Long, order: SortOrder<UserSongSort>) =
        listed(order, filter = OF_PLAYLIST) { it.bindLong(2, playlistId) }

    override fun origins(): Flow<Set<Origin>> =
        scope.observe(emptySet()) { userId -> userSongDao.origins(userId).map { it.toSet() } }

    /**
     * @param filter extra conditions after the user's, its `?` start at position 2
     * @param bind binds the values of [filter]
     */
    private fun paged(
        order: SortOrder<UserSongSort>,
        filter: String,
        bind: (SQLiteStatement) -> Unit = {},
    ): Flow<PagingData<UserSongView>> =
        scope.observe(PagingData.empty()) { userId ->
            pagedFlow(UserSongViewEntity::toModel) {
                userSongDao.paged(query(userId, order, filter, bind))
            }
        }

    /** Like [paged], every song at once. */
    private suspend fun listed(
        order: SortOrder<UserSongSort>,
        filter: String,
        bind: (SQLiteStatement) -> Unit = {},
    ): List<UserSongView> =
        userSongDao.list(query(scope.awaitId(), order, filter, bind))
            .map(UserSongViewEntity::toModel)

    private fun query(
        userId: String,
        order: SortOrder<UserSongSort>,
        filter: String,
        bind: (SQLiteStatement) -> Unit,
    ) = RoomRawQuery(
        sql = "SELECT * FROM user_song_view WHERE user_id = ? $filter ${order.toOrderBy("song_id")}",
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

        const val OF_ALBUM = "AND album_id = ?"
        const val OF_ARTIST =
            "AND song_id IN (SELECT song_id FROM song_artist_map WHERE artist_id = ?)"
        const val OF_PLAYLIST =
            "AND song_id IN (SELECT song_id FROM song_playlist_map WHERE playlist_id = ?)"
    }
}
