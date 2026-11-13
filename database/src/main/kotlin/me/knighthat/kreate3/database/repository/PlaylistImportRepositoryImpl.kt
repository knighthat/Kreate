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

import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.AlbumDao
import me.knighthat.kreate3.database.dao.ArtistDao
import me.knighthat.kreate3.database.dao.SongDao
import me.knighthat.kreate3.database.dao.UserPlaylistDao
import me.knighthat.kreate3.database.dao.UserSongDao
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.model.Origin
import kotlin.time.Clock

/**
 * Room backed implementation of [PlaylistImportRepository].
 */
internal class PlaylistImportRepositoryImpl(
    private val database: AppDatabase,
    private val songDao: SongDao,
    private val userSongDao: UserSongDao,
    private val playlistDao: UserPlaylistDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : PlaylistImportRepository {

    private val scope = UserScope(activeUser)

    override suspend fun storedSongs(origin: Origin): List<Song> =
        songDao.withOrigin(origin)
            .filter { it.unavailableSince == null }
            .map { it.toModel() }

    override suspend fun importPlaylist(
        name: String,
        songs: List<Song>,
        credits: Map<String, ImportedCredits>,
    ): ImportedPlaylist {
        val userId = scope.requireId()
        return database.inTransaction {
            val now = clock.now()
            val leftOut = ArrayList<String>()
            val kept = LinkedHashSet<String>()

            for (song in songs) {
                if (song.id in kept) {
                    leftOut += song.id
                    continue
                }
                val stored = songDao.get(song.id)
                when {
                    stored != null && stored.origin != song.origin -> {
                        leftOut += song.id
                        continue
                    }

                    stored != null -> userSongDao.insertAll(
                        listOf(UserSongEntity(userId, song.id, createdAt = now, updatedAt = now))
                    )

                    song.origin == Origin.LOCAL -> {
                        leftOut += song.id
                        continue
                    }

                    else -> {
                        val credit = credits[song.id]
                        // The album first, the song points at it
                        val album = credit?.album
                        if (album != null) albumDao.insertAll(listOf(album.toEntity(now, now)))
                        songDao.upsert(
                            song.copy(albumId = album?.id, unavailableSince = null)
                                .toEntity(now, now)
                        )
                        val artists = credit?.artists.orEmpty().distinctBy { it.id }
                        if (artists.isNotEmpty()) {
                            artistDao.insertAll(artists.map { it.toEntity(now, now) })
                            songDao.linkAll(artists.map { SongArtistMapEntity(song.id, it.id) })
                        }
                        userSongDao.insertAll(
                            listOf(
                                UserSongEntity(
                                    userId,
                                    song.id,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        )
                    }
                }
                kept += song.id
            }

            if (kept.isEmpty()) return@inTransaction ImportedPlaylist(null, 0, leftOut)

            val playlistId = playlistDao.upsert(
                UserPlaylistEntity(
                    userId = userId,
                    origin = Origin.LOCAL,
                    name = name,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            playlistDao.addSongs(
                kept.mapIndexed { position, songId ->
                    SongPlaylistMapEntity(songId, playlistId, position)
                }
            )
            ImportedPlaylist(playlistId, kept.size, leftOut)
        }
    }
}
