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

import androidx.paging.PagingData
import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.AppDatabase
import me.knighthat.kreate.database.dao.UserAlbumDao
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.UserAlbum
import me.knighthat.kreate.database.model.UserAlbumView
import me.knighthat.kreate.database.paging.SortOrder
import me.knighthat.kreate.database.paging.UserAlbumSort
import me.knighthat.kreate.database.paging.pagedFlow
import me.knighthat.kreate.database.paging.toOrderBy
import me.knighthat.kreate.database.view.UserAlbumViewEntity
import kotlin.time.Clock

/**
 * Room backed implementation of [UserAlbumRepository].
 */
internal class UserAlbumRepositoryImpl(
    private val database: AppDatabase,
    private val userAlbumDao: UserAlbumDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : UserAlbumRepository {

    private val scope = UserScope(activeUser)

    override fun all(order: SortOrder<UserAlbumSort>): Flow<PagingData<UserAlbumView>> =
        paged(order, filter = "")

    // A bookmark is a time, -1 is a dislike
    override fun bookmarked(order: SortOrder<UserAlbumSort>): Flow<PagingData<UserAlbumView>> =
        paged(order, filter = "AND bookmarked_at >= 0")

    private fun paged(
        order: SortOrder<UserAlbumSort>,
        filter: String
    ): Flow<PagingData<UserAlbumView>> =
        scope.observe(PagingData.empty()) { userId ->
            pagedFlow(UserAlbumViewEntity::toModel) {
                userAlbumDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM user_album_view WHERE user_id = ? $filter ${
                            order.toOrderBy(
                                "album_id"
                            )
                        }",
                        onBindStatement = { it.bindText(1, userId) },
                    )
                )
            }
        }

    override fun find(albumId: String): Flow<UserAlbumView?> =
        scope.observe(null) { userId ->
            userAlbumDao.find(userId, albumId).map { it?.toModel() }
        }

    override suspend fun save(album: UserAlbum) {
        val userId = scope.requireId()
        val now = clock.now()
        val createdAt = userAlbumDao.get(userId, album.albumId)?.createdAt ?: now
        userAlbumDao.upsert(album.toEntity(userId, createdAt, now))
    }

    override suspend fun update(albumId: String, transform: (UserAlbum) -> UserAlbum) {
        val userId = scope.requireId()
        database.inTransaction {
            val current = userAlbumDao.get(userId, albumId)
            val updated = transform(current?.toModel() ?: UserAlbum(albumId))
            require(updated.albumId == albumId) { "The album of a library entry cannot be changed" }

            val now = clock.now()
            userAlbumDao.upsert(updated.toEntity(userId, current?.createdAt ?: now, now))
        }
    }

    override suspend fun remove(albumId: String) =
        userAlbumDao.delete(scope.requireId(), albumId)
}
