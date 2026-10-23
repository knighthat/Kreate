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
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.UserAlbumDao
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.UserAlbum
import me.knighthat.kreate3.database.model.UserAlbumView
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserAlbumSort
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import me.knighthat.kreate3.database.view.UserAlbumViewEntity
import me.knighthat.kreate3.model.Origin
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

    override fun all(
        order: SortOrder<UserAlbumSort>,
        origins: Set<Origin>,
    ): Flow<PagingData<UserAlbumView>> = paged(order, filter = originFilter(origins))

    override suspend fun list(
        order: SortOrder<UserAlbumSort>,
        origins: Set<Origin>,
    ): List<UserAlbumView> {
        val userId = scope.awaitId()
        return userAlbumDao.list(
            RoomRawQuery(
                sql = "SELECT * FROM user_album_view WHERE user_id = ? ${originFilter(origins)} " +
                        order.toOrderBy("album_id"),
                onBindStatement = { it.bindText(1, userId) },
            )
        ).map(UserAlbumViewEntity::toModel)
    }

    override fun origins(): Flow<Set<Origin>> =
        scope.observe(emptySet()) { userId -> userAlbumDao.origins(userId).map { it.toSet() } }

    override suspend fun count(origins: Set<Origin>): Int {
        val userId = scope.awaitId()
        return userAlbumDao.count(
            RoomRawQuery(
                sql = "SELECT COUNT(*) FROM user_album_view WHERE user_id = ? ${originFilter(origins)}",
                onBindStatement = { it.bindText(1, userId) },
            )
        )
    }

    override suspend fun random(origins: Set<Origin>): UserAlbumView? {
        val userId = scope.awaitId()
        return userAlbumDao.first(
            RoomRawQuery(
                sql = "SELECT * FROM user_album_view WHERE user_id = ? ${originFilter(origins)} " +
                        "ORDER BY RANDOM() LIMIT 1",
                onBindStatement = { it.bindText(1, userId) },
            )
        )?.toModel()
    }

    // A bookmark is a time, -1 is a dislike
    override fun bookmarked(order: SortOrder<UserAlbumSort>): Flow<PagingData<UserAlbumView>> =
        paged(order, filter = "AND bookmarked_at >= 0")

    override fun search(query: String, order: SortOrder<UserAlbumSort>) =
        paged(
            order,
            filter = "AND (name LIKE ?2 ESCAPE '\\' OR artists LIKE ?2 ESCAPE '\\')",
        ) { it.bindText(2, containsPattern(query)) }

    /**
     * @param filter extra conditions after the user's, its `?` start at position 2
     * @param bind binds the values of [filter]
     */
    private fun paged(
        order: SortOrder<UserAlbumSort>,
        filter: String,
        bind: (SQLiteStatement) -> Unit = {},
    ): Flow<PagingData<UserAlbumView>> =
        scope.observe(PagingData.empty()) { userId ->
            database.pagedFlow(UserAlbumViewEntity::toModel, "user_album_view") {
                userAlbumDao.paged(
                    RoomRawQuery(
                        sql = "SELECT * FROM user_album_view WHERE user_id = ? $filter ${
                            order.toOrderBy(
                                "album_id"
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

    override fun ofArtist(artistId: String, limit: Int): Flow<List<UserAlbumView>> =
        scope.observe(emptyList()) { userId ->
            userAlbumDao.ofArtist(userId, artistId, limit)
                .map { albums -> albums.map(UserAlbumViewEntity::toModel) }
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

    override suspend fun removeIfEmpty(albumId: String): Boolean =
        userAlbumDao.deleteIfEmpty(scope.requireId(), albumId, Origin.LOCAL) > 0
}
