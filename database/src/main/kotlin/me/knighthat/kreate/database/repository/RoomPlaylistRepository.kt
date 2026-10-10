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
import kotlinx.coroutines.flow.map
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.dao.UserPlaylistDao
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate.database.entity.UserPlaylistEntity
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserPlaylist
import kotlin.time.Clock

/**
 * Room backed implementation of [PlaylistRepository].
 */
internal class RoomPlaylistRepository(
    private val playlistDao: UserPlaylistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : PlaylistRepository {

    private val scope = UserScope(activeUser)

    override fun all(): Flow<List<UserPlaylist>> =
        scope.observe(emptyList()) { userId ->
            playlistDao.all(userId).map { it.map(UserPlaylistEntity::toModel) }
        }

    override fun find(playlistId: Long): Flow<UserPlaylist?> =
        playlistDao.find(playlistId).map { it?.toModel() }

    override fun songsOf(playlistId: Long): Flow<List<Song>> =
        playlistDao.songsOf(playlistId).map { it.map(SongEntity::toModel) }

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

    override suspend fun addSong(playlistId: Long, songId: String) {
        ownedBy(scope.requireId(), playlistId)
        playlistDao.addSong(SongPlaylistMapEntity(songId, playlistId))
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
