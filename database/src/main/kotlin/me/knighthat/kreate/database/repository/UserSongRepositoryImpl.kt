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
import me.knighthat.kreate.database.dao.UserSongDao
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.UserSong
import me.knighthat.kreate.database.model.UserSongView
import me.knighthat.kreate.database.paging.SortOrder
import me.knighthat.kreate.database.paging.UserSongSort
import me.knighthat.kreate.database.paging.pagedFlow
import me.knighthat.kreate.database.paging.toOrderBy
import me.knighthat.kreate.database.view.UserSongViewEntity
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

    override fun all(order: SortOrder<UserSongSort>): Flow<PagingData<UserSongView>> =
        paged(order, filter = "")

    // A like is a time, -1 is a dislike
    override fun liked(order: SortOrder<UserSongSort>): Flow<PagingData<UserSongView>> =
        paged(order, filter = "AND liked_at >= 0")

    private fun paged(
        order: SortOrder<UserSongSort>,
        filter: String
    ): Flow<PagingData<UserSongView>> =
        scope.observe(PagingData.empty()) { userId ->
            pagedFlow(UserSongViewEntity::toModel) {
                userSongDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM user_song_view WHERE user_id = ? $filter ${
                            order.toOrderBy(
                                "song_id"
                            )
                        }",
                        onBindStatement = { it.bindText(1, userId) },
                    )
                )
            }
        }

    override fun find(songId: String): Flow<UserSongView?> =
        scope.observe(null) { userId ->
            userSongDao.find(userId, songId).map { it?.toModel() }
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
}
