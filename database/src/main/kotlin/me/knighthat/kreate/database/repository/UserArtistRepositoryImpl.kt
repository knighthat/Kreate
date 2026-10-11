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
import me.knighthat.kreate.database.AppDatabase
import me.knighthat.kreate.database.dao.UserArtistDao
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.UserArtist
import me.knighthat.kreate.database.model.UserArtistView
import me.knighthat.kreate.database.view.UserArtistViewEntity
import kotlin.time.Clock

/**
 * Room backed implementation of [UserArtistRepository].
 */
internal class UserArtistRepositoryImpl(
    private val database: AppDatabase,
    private val userArtistDao: UserArtistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : UserArtistRepository {

    private val scope = UserScope(activeUser)

    override fun all(): Flow<List<UserArtistView>> =
        scope.observe(emptyList()) { userId ->
            userArtistDao.all(userId).map { it.map(UserArtistViewEntity::toModel) }
        }

    override fun followed(): Flow<List<UserArtistView>> =
        scope.observe(emptyList()) { userId ->
            userArtistDao.followed(userId).map { it.map(UserArtistViewEntity::toModel) }
        }

    override fun find(artistId: String): Flow<UserArtistView?> =
        scope.observe(null) { userId ->
            userArtistDao.find(userId, artistId).map { it?.toModel() }
        }

    override suspend fun save(artist: UserArtist) {
        val userId = scope.requireId()
        val now = clock.now()
        val createdAt = userArtistDao.get(userId, artist.artistId)?.createdAt ?: now
        userArtistDao.upsert(artist.toEntity(userId, createdAt, now))
    }

    override suspend fun update(artistId: String, transform: (UserArtist) -> UserArtist) {
        val userId = scope.requireId()
        database.inTransaction {
            val current = userArtistDao.get(userId, artistId)
            val updated = transform(current?.toModel() ?: UserArtist(artistId))
            require(updated.artistId == artistId) { "The artist of a library entry cannot be changed" }

            val now = clock.now()
            userArtistDao.upsert(updated.toEntity(userId, current?.createdAt ?: now, now))
        }
    }

    override suspend fun remove(artistId: String) =
        userArtistDao.delete(scope.requireId(), artistId)
}
