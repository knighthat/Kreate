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
import me.knighthat.kreate.database.dao.ArtistDao
import me.knighthat.kreate.database.dao.UserArtistDao
import me.knighthat.kreate.database.entity.UserArtistEntity
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.UserArtist
import kotlin.time.Clock

/**
 * Room backed implementation of [ArtistRepository].
 */
internal class RoomArtistRepository(
    private val artistDao: ArtistDao,
    private val userArtistDao: UserArtistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : ArtistRepository {

    private val scope = UserScope(activeUser)

    override fun find(id: String): Flow<Artist?> = artistDao.find(id).map { it?.toModel() }

    override suspend fun save(artist: Artist) {
        val now = clock.now()
        val createdAt = artistDao.get(artist.id)?.createdAt ?: now
        artistDao.upsert(artist.toEntity(createdAt, now))
    }

    override suspend fun delete(id: String) = artistDao.delete(id)

    override fun library(): Flow<List<UserArtist>> =
        scope.observe(emptyList()) { userId ->
            userArtistDao.all(userId).map { it.map(UserArtistEntity::toModel) }
        }

    override fun followed(): Flow<List<UserArtist>> =
        scope.observe(emptyList()) { userId ->
            userArtistDao.followed(userId).map { it.map(UserArtistEntity::toModel) }
        }

    override fun findInLibrary(artistId: String): Flow<UserArtist?> =
        scope.observe(null) { userId ->
            userArtistDao.find(userId, artistId).map { it?.toModel() }
        }

    override suspend fun saveToLibrary(artist: UserArtist) {
        val userId = scope.requireId()
        val now = clock.now()
        val createdAt = userArtistDao.get(userId, artist.artistId)?.createdAt ?: now
        userArtistDao.upsert(artist.toEntity(userId, createdAt, now))
    }

    override suspend fun removeFromLibrary(artistId: String) =
        userArtistDao.delete(scope.requireId(), artistId)
}
