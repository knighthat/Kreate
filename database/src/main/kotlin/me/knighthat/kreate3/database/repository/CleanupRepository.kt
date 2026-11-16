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

import android.net.Uri

/**
 * Deletes what no user keeps, every user's at once: see [cleanUp].
 */
interface CleanupRepository {

    /** A playlist [cleanUp] deleted, the owner of its picture deletes the file. */
    data class RemovedPlaylist(val userId: String, val playlistId: Long, val thumbnail: Uri?)

    /** What [cleanUp] deleted, by kind. */
    data class Result(
        val librarySongs: Int,
        val libraryAlbums: Int,
        val libraryArtists: Int,
        val playlists: List<RemovedPlaylist>,
        val songs: Int,
        val albums: Int,
        val artists: Int,
    ) {
        val isEmpty: Boolean
            get() = librarySongs + libraryAlbums + libraryArtists + playlists.size +
                    songs + albums + artists == 0
    }

    /**
     * In one transaction, for every user: library rows of songs they changed nothing of, never
     * played and have in none of their playlists; of albums and artists they changed nothing of
     * (no bookmark or follow either); playlists without songs. Then songs, albums and artists
     * nobody keeps anymore (in a library, a playlist, a saved queue or the plays; songs with
     * lyrics imported from a file stay). Device songs, albums and artists are left to the
     * library scan. The file is compacted after, giving the room back.
     */
    suspend fun cleanUp(): Result
}
