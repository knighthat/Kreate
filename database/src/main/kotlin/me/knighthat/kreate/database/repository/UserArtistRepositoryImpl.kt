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
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.AppDatabase
import me.knighthat.kreate.database.dao.UserArtistDao
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.UserArtist
import me.knighthat.kreate.database.model.UserArtistView
import me.knighthat.kreate.database.paging.SortOrder
import me.knighthat.kreate.database.paging.UserArtistSort
import me.knighthat.kreate.database.paging.pagedFlow
import me.knighthat.kreate.database.paging.toOrderBy
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

    override fun all(order: SortOrder<UserArtistSort>): Flow<PagingData<UserArtistView>> =
        paged(order, filter = "")

    // A follow is a time, -1 is a dislike
    override fun followed(order: SortOrder<UserArtistSort>): Flow<PagingData<UserArtistView>> =
        paged(order, filter = "AND follow_since >= 0")

    override fun search(query: String, order: SortOrder<UserArtistSort>) =
        paged(order, filter = "AND name LIKE ?2 ESCAPE '\\'") {
            it.bindText(2, containsPattern(query))
        }

    /**
     * @param filter extra conditions after the user's, its `?` start at position 2
     * @param bind binds the values of [filter]
     */
    private fun paged(
        order: SortOrder<UserArtistSort>,
        filter: String,
        bind: (SQLiteStatement) -> Unit = {},
    ): Flow<PagingData<UserArtistView>> =
        scope.observe(PagingData.empty()) { userId ->
            pagedFlow(UserArtistViewEntity::toModel) {
                userArtistDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM user_artist_view WHERE user_id = ? $filter ${
                            order.toOrderBy(
                                "artist_id"
                            )
                        }",
                        onBindStatement = {
                            it.bindText(1, userId)
                            bind(it)
                        },
                    )
                )
            }
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
