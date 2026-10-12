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

import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.AppDatabase
import me.knighthat.kreate.database.dao.AlbumDao
import me.knighthat.kreate.database.dao.ArtistDao
import me.knighthat.kreate.database.dao.SongDao
import me.knighthat.kreate.database.dao.UserAlbumDao
import me.knighthat.kreate.database.dao.UserArtistDao
import me.knighthat.kreate.database.dao.UserSongDao
import me.knighthat.kreate.database.entity.AlbumEntity
import me.knighthat.kreate.database.entity.ArtistEntity
import me.knighthat.kreate.database.entity.SongArtistMapEntity
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.entity.UserAlbumEntity
import me.knighthat.kreate.database.entity.UserArtistEntity
import me.knighthat.kreate.database.entity.UserSongEntity
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.LocalSong
import me.knighthat.kreate.database.model.Origin
import kotlin.time.Clock

/**
 * Room backed implementation of [LocalCatalogRepository].
 */
internal class LocalCatalogRepositoryImpl(
    private val database: AppDatabase,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val userSongDao: UserSongDao,
    private val userAlbumDao: UserAlbumDao,
    private val userArtistDao: UserArtistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : LocalCatalogRepository {

    private val scope = UserScope(activeUser)

    override suspend fun sync(songs: List<LocalSong>) {
        if (songs.isEmpty()) return

        // Wait outside the transaction. Right after start-up the active user may not be known yet,
        // failing here makes the caller try again instead of leaving new songs without an owner.
        val userId = withTimeoutOrNull(ACTIVE_USER_TIMEOUT) { scope.awaitId() }
            ?: throw IllegalStateException("No active user to add the scanned songs to")

        database.inTransaction {
            val now = clock.now()

            val artists = songs.mapNotNull { it.artist }.distinctBy { it.id }
                .map { ArtistEntity(it.id, Origin.LOCAL, it.name, null, now, now) }
            val albums = songs.mapNotNull { it.album }.distinctBy { it.id }
                .map {
                    AlbumEntity(
                        it.id,
                        Origin.LOCAL,
                        it.name,
                        null,
                        it.year,
                        null,
                        null,
                        null,
                        now,
                        now
                    )
                }
            val songEntities = songs.distinctBy { it.id }.map {
                SongEntity(
                    id = it.id,
                    origin = Origin.LOCAL,
                    name = it.name,
                    thumbnail = it.thumbnail,
                    duration = it.duration,
                    albumId = it.album?.id,
                    albumPosition = it.albumPosition,
                    createdAt = now,
                    updatedAt = now,
                )
            }

            // Parents first, songs point at albums and links point at both
            val existingArtists = artistDao.withOrigin(Origin.LOCAL).associateBy { it.id }
            artistDao.upsertAll(
                changed(
                    incoming = artists,
                    existing = existingArtists,
                    id = ArtistEntity::id,
                    unchanged = { new, old ->
                        new.copy(
                            createdAt = old.createdAt,
                            updatedAt = old.updatedAt
                        ) == old
                    },
                    keepCreation = { new, old -> new.copy(createdAt = old.createdAt) },
                )
            )

            val existingAlbums = albumDao.withOrigin(Origin.LOCAL).associateBy { it.id }
            albumDao.upsertAll(
                changed(
                    incoming = albums,
                    existing = existingAlbums,
                    id = AlbumEntity::id,
                    unchanged = { new, old ->
                        new.copy(
                            createdAt = old.createdAt,
                            updatedAt = old.updatedAt
                        ) == old
                    },
                    keepCreation = { new, old -> new.copy(createdAt = old.createdAt) },
                )
            )

            val existingSongs = songDao.withOrigin(Origin.LOCAL).associateBy { it.id }
            songDao.upsertAll(
                changed(
                    incoming = songEntities,
                    existing = existingSongs,
                    id = SongEntity::id,
                    unchanged = { new, old ->
                        new.copy(
                            createdAt = old.createdAt,
                            updatedAt = old.updatedAt
                        ) == old
                    },
                    keepCreation = { new, old -> new.copy(createdAt = old.createdAt) },
                )
            )

            val wanted = songs.distinctBy { it.id }
                .mapNotNull { song -> song.artist?.let { SongArtistMapEntity(song.id, it.id) } }
                .toSet()
            val linked = songDao.linksOfOrigin(Origin.LOCAL).toSet()
            (linked - wanted).takeIf { it.isNotEmpty() }?.let { songDao.unlinkAll(it.toList()) }
            (wanted - linked).takeIf { it.isNotEmpty() }?.let { songDao.linkAll(it.toList()) }

            // Every found item gets a library row for the user unless it already has one, so the
            // user's own values on existing rows are kept
            userSongDao.insertAll(songEntities.map {
                UserSongEntity(
                    userId,
                    it.id,
                    createdAt = now,
                    updatedAt = now
                )
            })
            userAlbumDao.insertAll(albums.map {
                UserAlbumEntity(
                    userId,
                    it.id,
                    createdAt = now,
                    updatedAt = now
                )
            })
            userArtistDao.insertAll(artists.map {
                UserArtistEntity(
                    userId,
                    it.id,
                    createdAt = now,
                    updatedAt = now
                )
            })

            // A song the scan didn't find may only be on a card that is out, keep it and its
            // users' data. The time it went missing is kept from the first scan that missed it.
            val found = songEntities.map { it.id }.toSet()
            songDao.upsertAll(
                existingSongs.values
                    .filter { it.id !in found && it.unavailableSince == null }
                    .map { it.copy(unavailableSince = now, updatedAt = now) },
            )
        }
    }

    /**
     * The [incoming] rows that are new or differ from [existing]. Rows that match what is stored
     * are skipped, a scan finds mostly those. Changed rows keep the stored creation time.
     */
    private fun <E : Any> changed(
        incoming: List<E>,
        existing: Map<String, E>,
        id: (E) -> String,
        unchanged: (new: E, old: E) -> Boolean,
        keepCreation: (new: E, old: E) -> E,
    ): List<E> = incoming.mapNotNull { new ->
        val old = existing[id(new)] ?: return@mapNotNull new
        if (unchanged(new, old)) null else keepCreation(new, old)
    }


    private companion object {

        const val ACTIVE_USER_TIMEOUT = 10_000L
    }
}
