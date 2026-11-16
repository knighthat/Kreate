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

import androidx.room.useWriterConnection
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.CleanupDao
import me.knighthat.kreate3.database.inTransaction

/**
 * Room backed implementation of [CleanupRepository].
 */
internal class CleanupRepositoryImpl(
    private val database: AppDatabase,
    private val dao: CleanupDao,
) : CleanupRepository {

    override suspend fun cleanUp(): CleanupRepository.Result {
        val result = database.inTransaction {
            // Library rows first, the songs, albums and artists they leave unused go after
            val librarySongs = dao.deleteUnusedUserSongs()
            val libraryAlbums = dao.deleteUnusedUserAlbums()
            val libraryArtists = dao.deleteUnusedUserArtists()
            val playlists = dao.emptyPlaylists().map {
                CleanupRepository.RemovedPlaylist(it.userId, it.playlistId, it.thumbnail)
            }
            dao.deleteEmptyPlaylists()
            val songs = dao.deleteUnusedSongs()
            CleanupRepository.Result(
                librarySongs = librarySongs,
                libraryAlbums = libraryAlbums,
                libraryArtists = libraryArtists,
                playlists = playlists,
                songs = songs,
                albums = dao.deleteUnusedAlbums(),
                artists = dao.deleteUnusedArtists(),
            )
        }
        // Outside a transaction, SQLite can't compact inside one
        if (!result.isEmpty)
            database.useWriterConnection { connection -> connection.usePrepared("VACUUM") { it.step() } }
        return result
    }
}
