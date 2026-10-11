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
import me.knighthat.kreate.database.dao.UserDao
import me.knighthat.kreate.database.entity.UserEntity
import me.knighthat.kreate.database.model.User
import kotlin.time.Clock

/**
 * Room backed implementation of [UserRepository].
 */
internal class UserRepositoryImpl(
    private val userDao: UserDao,
    private val clock: Clock,
) : UserRepository {

    override fun all(): Flow<List<User>> = userDao.all().map { it.map(UserEntity::toModel) }

    override fun find(id: String): Flow<User?> = userDao.find(id).map { it?.toModel() }

    override suspend fun save(user: User) {
        val now = clock.now()
        val createdAt = userDao.get(user.id)?.createdAt ?: now
        userDao.upsert(user.toEntity(createdAt, now))
    }

    override suspend fun rename(id: String, name: String) {
        require(name.isNotBlank()) { "A profile needs a name" }

        userDao.setName(id, name, clock.now())
    }

    // Not a content change of the profile, so `updated_at` stays as it is
    override suspend fun markActive(id: String) = userDao.setActiveSince(id, clock.now())

    override suspend fun markSeen(id: String) = userDao.setLastSeen(id, clock.now())

    override suspend fun delete(id: String) = userDao.delete(id)
}
