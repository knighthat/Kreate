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
import me.knighthat.kreate.database.dao.UserAlbumDao
import me.knighthat.kreate.database.dao.UserArtistDao
import me.knighthat.kreate.database.dao.UserPlaylistDao
import me.knighthat.kreate.database.entity.UserAlbumEntity
import me.knighthat.kreate.database.entity.UserArtistEntity
import me.knighthat.kreate.database.entity.UserPlaylistEntity
import me.knighthat.kreate.database.inTransaction
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.UserPlaylist
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Room backed implementation of [RemoteCatalogRepository].
 */
internal class RemoteCatalogRepositoryImpl(
    private val database: AppDatabase,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val userAlbumDao: UserAlbumDao,
    private val userArtistDao: UserArtistDao,
    private val playlistDao: UserPlaylistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : RemoteCatalogRepository {

    private val scope = UserScope(activeUser)

    override suspend fun syncAlbums(origin: Origin, albums: List<RemoteItem<Album>>) {
        requireOrigin(origin, albums.map { it.item.origin })
        val userId = awaitUser()

        database.inTransaction {
            val now = clock.now()
            val incoming = albums.distinctBy { it.item.id }

            val stored = albumDao.withOrigin(origin).associateBy { it.id }
            albumDao.upsertAll(incoming.mapNotNull { (album, _) ->
                val old = stored[album.id]
                // The server doesn't list share links, keep the one made for the album
                val new = album.toEntity(old?.createdAt ?: now, now)
                    .let { if (it.url == null && old != null) it.copy(url = old.url) else it }
                new.takeUnless { old != null && it.copy(updatedAt = old.updatedAt) == old }
            })

            val library = userAlbumDao.withOrigin(userId, origin).associateBy { it.albumId }
            userAlbumDao.upsertAll(incoming.mapNotNull { (album, starredAt) ->
                val old = library[album.id]
                    ?: return@mapNotNull UserAlbumEntity(
                        userId,
                        album.id,
                        bookmarkedAt = starredAt,
                        createdAt = now,
                        updatedAt = now,
                    )
                val bookmarkedAt = reaction(starredAt, old.bookmarkedAt)
                if (bookmarkedAt == old.bookmarkedAt) null
                else old.copy(bookmarkedAt = bookmarkedAt, updatedAt = now)
            })

            val kept = incoming.map { it.item.id }.toSet()
            userAlbumDao.deleteAll(library.values.filter { it.albumId !in kept })
        }
    }

    override suspend fun syncArtists(origin: Origin, artists: List<RemoteItem<Artist>>) {
        requireOrigin(origin, artists.map { it.item.origin })
        val userId = awaitUser()

        database.inTransaction {
            val now = clock.now()
            val incoming = artists.distinctBy { it.item.id }

            val stored = artistDao.withOrigin(origin).associateBy { it.id }
            artistDao.upsertAll(incoming.mapNotNull { (artist, _) ->
                val old = stored[artist.id]
                val new = artist.toEntity(old?.createdAt ?: now, now)
                new.takeUnless { old != null && it.copy(updatedAt = old.updatedAt) == old }
            })

            val library = userArtistDao.withOrigin(userId, origin).associateBy { it.artistId }
            userArtistDao.upsertAll(incoming.mapNotNull { (artist, starredAt) ->
                val old = library[artist.id]
                    ?: return@mapNotNull UserArtistEntity(
                        userId,
                        artist.id,
                        followSince = starredAt,
                        createdAt = now,
                        updatedAt = now,
                    )
                val followSince = reaction(starredAt, old.followSince)
                if (followSince == old.followSince) null
                else old.copy(followSince = followSince, updatedAt = now)
            })

            val kept = incoming.map { it.item.id }.toSet()
            userArtistDao.deleteAll(library.values.filter { it.artistId !in kept })
        }
    }

    override suspend fun syncPlaylists(origin: Origin, playlists: List<UserPlaylist>) {
        playlists.forEach { requireNotNull(it.remoteId) { "A playlist of a server needs its remote id" } }
        requireOrigin(origin, playlists.map { it.origin })
        val userId = awaitUser()

        database.inTransaction {
            val now = clock.now()
            val incoming = playlists.distinctBy { it.remoteId }

            val stored = playlistDao.withRemoteOrigin(userId, origin).associateBy { it.remoteId }
            playlistDao.upsertAll(incoming.mapNotNull { playlist ->
                val old = stored[playlist.remoteId]
                    ?: return@mapNotNull UserPlaylistEntity(
                        userId = userId,
                        origin = playlist.origin,
                        name = playlist.name,
                        thumbnail = playlist.thumbnail,
                        createdAt = now,
                        updatedAt = now,
                        remoteId = playlist.remoteId,
                    )
                if (old.name == playlist.name && old.thumbnail == playlist.thumbnail) null
                else old.copy(name = playlist.name, thumbnail = playlist.thumbnail, updatedAt = now)
            })

            val kept = incoming.map { it.remoteId }.toSet()
            playlistDao.deleteAll(stored.values.filter { it.remoteId !in kept })
        }
    }

    /**
     * Wait outside the transaction. Right after start-up the active user may not be known yet,
     * failing here makes the caller try again instead of storing items without an owner.
     */
    private suspend fun awaitUser(): String =
        withTimeoutOrNull(ACTIVE_USER_TIMEOUT) { scope.awaitId() }
            ?: throw IllegalStateException("No active user to add the server's library to")

    private fun requireOrigin(origin: Origin, origins: List<Origin>) =
        require(origins.all { it == origin }) { "Every item must be from $origin" }

    /**
     * The reaction to keep: the server's star when there is one, otherwise a dislike stays and
     * anything else is cleared, the server is where the star was taken away.
     */
    private fun reaction(starredAt: Instant?, stored: Instant?): Instant? = when {
        starredAt != null -> starredAt
        stored?.toEpochMilliseconds() == DISLIKED -> stored
        else -> null
    }

    private companion object {

        const val ACTIVE_USER_TIMEOUT = 10_000L

        /** Stored in a reaction column for a dislike, see `UserAlbum.bookmarkedAt`. */
        const val DISLIKED = -1L
    }
}
