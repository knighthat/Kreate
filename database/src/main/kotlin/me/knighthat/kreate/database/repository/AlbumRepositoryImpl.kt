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
import me.knighthat.kreate.database.AppDatabase
import me.knighthat.kreate.database.dao.AlbumDao
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.paging.SongSort
import me.knighthat.kreate.database.paging.SortOrder
import me.knighthat.kreate.database.paging.pagedFlow
import me.knighthat.kreate.database.paging.toOrderBy
import kotlin.time.Clock

/**
 * Room backed implementation of [AlbumRepository].
 */
internal class AlbumRepositoryImpl(
    private val database: AppDatabase,
    private val albumDao: AlbumDao,
    private val clock: Clock,
) : AlbumRepository {

    override fun find(albumId: String): Flow<Album?> = albumDao.find(albumId).map { it?.toModel() }

    override fun songsOf(albumId: String, order: SortOrder<SongSort>): Flow<PagingData<Song>> =
        pagedFlow(SongEntity::toModel) {
            albumDao.pagedSongs(
                RoomRawQuery(
                    sql = "SELECT * FROM song WHERE album_id = ? ${order.toOrderBy("song.id")}",
                    onBindStatement = { it.bindText(1, albumId) },
                )
            )
        }

    override suspend fun save(album: Album) {
        val now = clock.now()
        val createdAt = albumDao.get(album.id)?.createdAt ?: now
        albumDao.upsert(album.toEntity(createdAt, now))
    }

    override suspend fun delete(albumId: String) = albumDao.delete(albumId)

    override suspend fun update(albumId: String, transform: (Album) -> Album) {
        database.inTransaction {
            val current = requireNotNull(albumDao.get(albumId)) { "Album $albumId doesn't exist" }
            val updated = transform(current.toModel())
            require(updated.id == albumId) { "The id of an album cannot be changed" }

            albumDao.upsert(updated.toEntity(current.createdAt, clock.now()))
        }
    }
}
