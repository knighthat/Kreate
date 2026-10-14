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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.ArtistDao
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.Artist
import kotlin.time.Clock

/**
 * Room backed implementation of [ArtistRepository].
 */
internal class ArtistRepositoryImpl(
    private val database: AppDatabase,
    private val artistDao: ArtistDao,
    private val clock: Clock,
) : ArtistRepository {

    override fun find(artistId: String): Flow<Artist?> =
        artistDao.find(artistId).map { it?.toModel() }

    override suspend fun save(artist: Artist) {
        val now = clock.now()
        val createdAt = artistDao.get(artist.id)?.createdAt ?: now
        artistDao.upsert(artist.toEntity(createdAt, now))
    }

    override suspend fun delete(artistId: String) = artistDao.delete(artistId)

    override suspend fun update(artistId: String, transform: (Artist) -> Artist) {
        database.inTransaction {
            val current =
                requireNotNull(artistDao.get(artistId)) { "Artist $artistId doesn't exist" }
            val updated = transform(current.toModel())
            require(updated.id == artistId) { "The id of a artist cannot be changed" }

            artistDao.upsert(updated.toEntity(current.createdAt, clock.now()))
        }
    }
}
