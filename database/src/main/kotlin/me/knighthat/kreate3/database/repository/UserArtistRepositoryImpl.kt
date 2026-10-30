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
import me.knighthat.kreate3.database.dao.UserArtistDao
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.UserArtist
import me.knighthat.kreate3.database.model.UserArtistView
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserArtistSort
import me.knighthat.kreate3.database.paging.pagedFlow
import me.knighthat.kreate3.database.paging.toOrderBy
import me.knighthat.kreate3.database.view.UserArtistViewEntity
import me.knighthat.kreate3.model.Origin
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

    override fun all(
        order: SortOrder<UserArtistSort>,
        origins: Set<Origin>,
        followedOnly: Boolean,
    ): Flow<PagingData<UserArtistView>> = paged(order, filter = filter(origins, followedOnly))

    override suspend fun list(
        order: SortOrder<UserArtistSort>,
        origins: Set<Origin>,
    ): List<UserArtistView> {
        val userId = scope.awaitId()
        return userArtistDao.list(
            RoomRawQuery(
                sql = "SELECT * FROM user_artist_view WHERE user_id = ? ${originFilter(origins)} " +
                        order.toOrderBy("artist_id"),
                onBindStatement = { it.bindText(1, userId) },
            )
        ).map(UserArtistViewEntity::toModel)
    }

    override fun origins(): Flow<Set<Origin>> =
        scope.observe(emptySet()) { userId -> userArtistDao.origins(userId).map { it.toSet() } }

    override suspend fun count(origins: Set<Origin>, followedOnly: Boolean): Int {
        val userId = scope.awaitId()
        return userArtistDao.count(
            RoomRawQuery(
                sql = "SELECT COUNT(*) FROM user_artist_view WHERE user_id = ? " +
                        filter(origins, followedOnly),
                onBindStatement = { it.bindText(1, userId) },
            )
        )
    }

    override suspend fun random(origins: Set<Origin>, followedOnly: Boolean): UserArtistView? {
        val userId = scope.awaitId()
        return userArtistDao.first(
            RoomRawQuery(
                sql = "SELECT * FROM user_artist_view WHERE user_id = ? " +
                        "${filter(origins, followedOnly)} " +
                        "ORDER BY RANDOM() LIMIT 1",
                onBindStatement = { it.bindText(1, userId) },
            )
        )?.toModel()
    }

    override fun followed(order: SortOrder<UserArtistSort>): Flow<PagingData<UserArtistView>> =
        paged(order, filter = FOLLOWED)

    override fun search(query: String, order: SortOrder<UserArtistSort>) =
        paged(order, filter = SEARCH) { it.bindText(2, containsPattern(query)) }

    override suspend fun listSearch(
        query: String,
        order: SortOrder<UserArtistSort>,
    ): List<UserArtistView> {
        val userId = scope.awaitId()
        return userArtistDao.list(
            RoomRawQuery(
                sql = "SELECT * FROM user_artist_view WHERE user_id = ? $SEARCH " +
                        order.toOrderBy("artist_id"),
                onBindStatement = {
                    it.bindText(1, userId)
                    it.bindText(2, containsPattern(query))
                },
            )
        ).map(UserArtistViewEntity::toModel)
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
            database.pagedFlow(UserArtistViewEntity::toModel, "user_artist_view") {
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

    override suspend fun removeIfEmpty(artistId: String): Boolean =
        userArtistDao.deleteIfEmpty(scope.requireId(), artistId, Origin.LOCAL) > 0

    private companion object {

        // ?2 is the pattern of what is searched
        const val SEARCH = "AND name LIKE ?2 ESCAPE '\\'"

        // A follow is a time, -1 is a dislike
        const val FOLLOWED = "AND follow_since >= 0"

        fun filter(origins: Set<Origin>, followedOnly: Boolean) =
            originFilter(origins) + if (followedOnly) " $FOLLOWED" else ""
    }
}
