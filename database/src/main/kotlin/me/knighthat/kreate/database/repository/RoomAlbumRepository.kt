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
import me.knighthat.kreate.database.dao.AlbumDao
import me.knighthat.kreate.database.dao.UserAlbumDao
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.entity.UserAlbumEntity
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserAlbum
import kotlin.time.Clock

/**
 * Room backed implementation of [AlbumRepository].
 */
internal class RoomAlbumRepository(
    private val albumDao: AlbumDao,
    private val userAlbumDao: UserAlbumDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : AlbumRepository {

    private val scope = UserScope(activeUser)

    override fun find(id: String): Flow<Album?> = albumDao.find(id).map { it?.toModel() }

    override fun songsOf(albumId: String): Flow<List<Song>> =
        albumDao.songsOf(albumId).map { it.map(SongEntity::toModel) }

    override suspend fun save(album: Album) {
        val now = clock.now()
        val createdAt = albumDao.get(album.id)?.createdAt ?: now
        albumDao.upsert(album.toEntity(createdAt, now))
    }

    override suspend fun delete(id: String) = albumDao.delete(id)

    override fun library(): Flow<List<UserAlbum>> =
        scope.observe(emptyList()) { userId ->
            userAlbumDao.all(userId).map { it.map(UserAlbumEntity::toModel) }
        }

    override fun bookmarked(): Flow<List<UserAlbum>> =
        scope.observe(emptyList()) { userId ->
            userAlbumDao.bookmarked(userId).map { it.map(UserAlbumEntity::toModel) }
        }

    override fun findInLibrary(albumId: String): Flow<UserAlbum?> =
        scope.observe(null) { userId ->
            userAlbumDao.find(userId, albumId).map { it?.toModel() }
        }

    override suspend fun saveToLibrary(album: UserAlbum) {
        val userId = scope.requireId()
        val now = clock.now()
        val createdAt = userAlbumDao.get(userId, album.albumId)?.createdAt ?: now
        userAlbumDao.upsert(album.toEntity(userId, createdAt, now))
    }

    override suspend fun removeFromLibrary(albumId: String) =
        userAlbumDao.delete(scope.requireId(), albumId)
}
