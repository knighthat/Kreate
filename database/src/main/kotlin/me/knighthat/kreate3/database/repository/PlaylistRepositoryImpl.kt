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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.UserPlaylistDao
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.paging.PlaylistSort
import me.knighthat.kreate3.database.paging.SongSort
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import me.knighthat.kreate3.model.Origin
import kotlin.time.Clock

/**
 * Room backed implementation of [PlaylistRepository].
 */
internal class PlaylistRepositoryImpl(
    private val database: AppDatabase,
    private val playlistDao: UserPlaylistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : PlaylistRepository {

    private val scope = UserScope(activeUser)

    override fun all(
        order: SortOrder<PlaylistSort>,
        origins: Set<Origin>,
    ): Flow<PagingData<UserPlaylist>> =
        scope.observe(PagingData.empty()) { userId ->
            database.pagedFlow(UserPlaylistEntity::toModel, "user_playlist") {
                playlistDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM user_playlist WHERE user_id = ? ${originFilter(origins)} " +
                                order.toOrderBy("playlist_id"),
                        onBindStatement = { it.bindText(1, userId) },
                    )
                )
            }
        }

    override suspend fun list(
        order: SortOrder<PlaylistSort>,
        origins: Set<Origin>,
    ): List<UserPlaylist> {
        val userId = scope.awaitId()
        return playlistDao.list(
            RoomRawQuery(
                sql = "SELECT * FROM user_playlist WHERE user_id = ? ${originFilter(origins)} " +
                        order.toOrderBy("playlist_id"),
                onBindStatement = { it.bindText(1, userId) },
            )
        ).map(UserPlaylistEntity::toModel)
    }

    override fun origins(): Flow<Set<Origin>> =
        scope.observe(emptySet()) { userId -> playlistDao.origins(userId).map { it.toSet() } }

    override suspend fun count(origins: Set<Origin>): Int {
        val userId = scope.awaitId()
        return playlistDao.count(
            RoomRawQuery(
                sql = "SELECT COUNT(*) FROM user_playlist WHERE user_id = ? ${originFilter(origins)}",
                onBindStatement = { it.bindText(1, userId) },
            )
        )
    }

    override suspend fun random(origins: Set<Origin>): UserPlaylist? {
        val userId = scope.awaitId()
        return playlistDao.first(
            RoomRawQuery(
                sql = "SELECT * FROM user_playlist WHERE user_id = ? ${originFilter(origins)} " +
                        "ORDER BY RANDOM() LIMIT 1",
                onBindStatement = { it.bindText(1, userId) },
            )
        )?.toModel()
    }

    override fun search(
        query: String,
        order: SortOrder<PlaylistSort>
    ): Flow<PagingData<UserPlaylist>> =
        scope.observe(PagingData.empty()) { userId ->
            database.pagedFlow(UserPlaylistEntity::toModel, "user_playlist") {
                playlistDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM user_playlist WHERE user_id = ? AND name LIKE ? ESCAPE '\\' " +
                                order.toOrderBy("playlist_id"),
                        onBindStatement = {
                            it.bindText(1, userId)
                            it.bindText(2, containsPattern(query))
                        },
                    )
                )
            }
        }

    override fun find(playlistId: Long): Flow<UserPlaylist?> =
        playlistDao.find(playlistId).map { it?.toModel() }

    override fun songsOf(playlistId: Long, order: SortOrder<SongSort>): Flow<PagingData<Song>> =
        database.pagedFlow(SongEntity::toModel, "song", "song_playlist_map") {
            playlistDao.pagedSongs(
                RoomRawQuery(
                    sql = "SELECT song.* FROM song " +
                            "JOIN song_playlist_map ON song_playlist_map.song_id = song.id " +
                            "WHERE song_playlist_map.playlist_id = ? ${order.toOrderBy("song.id")}",
                    onBindStatement = { it.bindLong(1, playlistId) },
                )
            )
        }

    override suspend fun save(playlist: UserPlaylist): Long {
        val userId = scope.requireId()
        val now = clock.now()

        val existing = if (playlist.id == 0L) null else ownedBy(userId, playlist.id)
        val createdAt = existing?.createdAt ?: now

        val rowId = playlistDao.upsert(playlist.toEntity(userId, createdAt, now))
        // Room reports -1 when it updated an existing row
        return if (rowId == -1L) playlist.id else rowId
    }

    override suspend fun delete(playlistId: Long) =
        playlistDao.delete(playlistId, scope.requireId())

    override suspend fun addSong(playlistId: Long, songId: String): Boolean {
        ownedBy(scope.requireId(), playlistId)
        return playlistDao.addSong(SongPlaylistMapEntity(songId, playlistId)) != -1L
    }

    override suspend fun removeSong(playlistId: Long, songId: String) {
        ownedBy(scope.requireId(), playlistId)
        playlistDao.removeSong(SongPlaylistMapEntity(songId, playlistId))
    }

    private suspend fun ownedBy(userId: String, playlistId: Long): UserPlaylistEntity {
        val playlist = playlistDao.get(playlistId)
        require(playlist != null && playlist.userId == userId) {
            "Playlist $playlistId doesn't exist or belongs to another user"
        }
        return playlist
    }
}
