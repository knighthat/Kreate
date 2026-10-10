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
import me.knighthat.kreate.database.dao.PersistentQueueDao
import me.knighthat.kreate.database.entity.PersistentQueueEntity
import me.knighthat.kreate.database.model.QueuedSong

/**
 * Room backed implementation of [QueueRepository].
 */
internal class QueueRepositoryImpl(
    private val queueDao: PersistentQueueDao,
    activeUser: ActiveUser,
) : QueueRepository {

    private val scope = UserScope(activeUser)

    override fun queue(): Flow<List<QueuedSong>> =
        scope.observe(emptyList()) { userId ->
            queueDao.queue(userId).map { it.map(PersistentQueueEntity::toModel) }
        }

    override suspend fun replace(songIds: List<String>) {
        val userId = scope.requireId()
        val items = songIds.distinct()
            .mapIndexed { index, songId -> PersistentQueueEntity(songId, userId, index) }
        queueDao.replace(userId, items)
    }

    override suspend fun clear() = queueDao.clear(scope.requireId())
}
