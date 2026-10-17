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

import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.AlbumDao
import me.knighthat.kreate3.database.dao.ArtistDao
import me.knighthat.kreate3.database.dao.SongDao
import me.knighthat.kreate3.database.dao.UserSongDao
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Room backed implementation of [RemoteCatalogRepository].
 */
internal class RemoteCatalogRepositoryImpl(
    private val database: AppDatabase,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val songDao: SongDao,
    private val userSongDao: UserSongDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : RemoteCatalogRepository {

    private val scope = UserScope(activeUser)

    override suspend fun syncLikedSongs(
        origin: Origin,
        songs: List<RemoteSong>,
        complete: Boolean
    ) {
        requireOrigin(origin, songs.origins())
        val userId = awaitUser()

        database.inTransaction {
            val now = clock.now()
            val incoming = songs.distinctBy { it.song.id }
            val library = storeSongs(userId, origin, now, incoming)

            if (!complete) return@inTransaction
            // Only the like goes, the song stays in the user's library
            val kept = incoming.map { it.song.id }.toSet()
            userSongDao.upsertAll(
                library.filter { it.songId !in kept && it.likedAt.isLike }
                    .map { it.copy(likedAt = null, updatedAt = now) }
            )
        }
    }

    override suspend fun savePlayedSong(song: RemoteSong) {
        val origin = song.song.origin
        require(origin != Origin.LOCAL) { "${song.song.id} is a song of the device" }
        requireOrigin(origin, listOf(song).origins())
        val userId = awaitUser()

        database.inTransaction {
            val now = clock.now()
            storeCatalog(origin, now, listOf(song))
            // Only adds it, a song already in the library keeps its like and overrides
            userSongDao.insertAll(
                listOf(UserSongEntity(userId, song.song.id, createdAt = now, updatedAt = now))
            )
        }
    }

    /**
     * Upserts [incoming] with what they point at and likes each in the user's library, see
     * [syncLikedSongs].
     *
     * @return the user's rows of [origin] as they were before
     */
    private suspend fun storeSongs(
        userId: String,
        origin: Origin,
        now: Instant,
        incoming: List<RemoteSong>,
    ): Collection<UserSongEntity> {
        storeCatalog(origin, now, incoming)

        val library = userSongDao.withOrigin(userId, origin).associateBy { it.songId }
        userSongDao.upsertAll(incoming.mapIndexedNotNull { index, remote ->
            val old = library[remote.song.id]
            val likedAt = remote.starredAt
                ?: old?.likedAt?.takeIf { it.isLike }
                ?: (now - index.milliseconds)

            when {
                old == null -> UserSongEntity(
                    userId,
                    remote.song.id,
                    likedAt = likedAt,
                    createdAt = now,
                    updatedAt = now,
                )

                likedAt == old.likedAt -> null
                else -> old.copy(likedAt = likedAt, updatedAt = now)
            }
        })
        return library.values
    }

    /**
     * Upserts [incoming] in the shared catalog with what they point at, see [syncLikedSongs].
     * The user's library isn't touched.
     */
    private suspend fun storeCatalog(origin: Origin, now: Instant, incoming: List<RemoteSong>) {
        // What the songs point at must exist, rows already stored keep their full values
        albumDao.insertAll(incoming.mapNotNull { it.album }.distinctBy { it.id }
            .map { it.toEntity(now, now) })
        artistDao.insertAll(incoming.flatMap { it.artists }.distinctBy { it.id }
            .map { it.toEntity(now, now) })

        val stored = songDao.withOrigin(origin).associateBy { it.id }
        songDao.upsertAll(incoming.mapNotNull { (song) ->
            val old = stored[song.id]
            // The server has the song, so it is available again
            val new = song.toEntity(old?.createdAt ?: now, now).copy(unavailableSince = null)
            new.takeUnless { old != null && it.copy(updatedAt = old.updatedAt) == old }
        })

        // Each song is credited to exactly the artists the server names
        val ids = incoming.map { it.song.id }.toSet()
        val wanted = incoming.flatMap { remote ->
            remote.artists.map { SongArtistMapEntity(remote.song.id, it.id) }
        }.toSet()
        val linked = songDao.linksOfOrigin(origin).filter { it.songId in ids }.toSet()
        (linked - wanted).takeIf { it.isNotEmpty() }?.let { songDao.unlinkAll(it.toList()) }
        (wanted - linked).takeIf { it.isNotEmpty() }?.let { songDao.linkAll(it.toList()) }
    }

    /**
     * Wait outside the transaction. Right after start-up the active user may not be known yet,
     * failing here makes the caller try again instead of storing items without an owner.
     */
    private suspend fun awaitUser(): String =
        withTimeoutOrNull(ACTIVE_USER_TIMEOUT) { scope.awaitId() }
            ?: throw IllegalStateException("No active user to add the server's library to")

    /** The origin of each song and of everything it points at. */
    private fun List<RemoteSong>.origins(): List<Origin> =
        flatMap { listOfNotNull(it.song.origin, it.album?.origin) + it.artists.map(Artist::origin) }

    /** Whether a reaction column holds a like, not a dislike or nothing. */
    private val Instant?.isLike: Boolean
        get() = this != null && toEpochMilliseconds() != DISLIKED

    private fun requireOrigin(origin: Origin, origins: List<Origin>) =
        require(origins.all { it == origin }) { "Every item must be from $origin" }

    private companion object {

        const val ACTIVE_USER_TIMEOUT = 10_000L

        /** Stored in a reaction column for a dislike, see `UserSong.likedAt`. */
        const val DISLIKED = -1L
    }
}
