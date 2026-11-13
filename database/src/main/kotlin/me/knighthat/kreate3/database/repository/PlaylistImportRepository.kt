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

import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.model.Origin

/**
 * What [PlaylistImportRepository.importPlaylist] did.
 *
 * @property playlistId the new playlist, `null` when no song could go in it and none was made
 * @property added how many songs the playlist holds
 * @property leftOut the ids of the songs left out, in the order given
 */
data class ImportedPlaylist(val playlistId: Long?, val added: Int, val leftOut: List<String>)

/**
 * Makes the active user's local playlists out of songs read from a file, like an exported
 * playlist. Nothing is fetched: songs are stored from the file's values.
 */
interface PlaylistImportRepository {

    /**
     * The songs of [origin] in the shared catalog, without the ones a scan misses now.
     */
    suspend fun storedSongs(origin: Origin): List<Song>

    /**
     * Creates the local playlist [name] holding [songs], as the file has them with their ids at
     * their origins, in their order, all in one transaction.
     *
     * - A song stored with the same id and origin goes in as it is stored, the file's values
     *   don't change it.
     * - A song not stored yet is stored from the file's values, unless it is of the device: the
     *   scan stores those, so one not stored is left out. A song whose id is stored for another
     *   origin is left out too.
     * - Every song that goes in joins the user's library without a like or values of the user's
     *   own (the file's artists aren't kept: files carry no artist ids), a song already there keeps
     *   its row.
     * - A song given twice goes in once, the second is left out.
     *
     * No playlist is made when every song is left out.
     *
     * @throws IllegalStateException when no user is active
     */
    suspend fun importPlaylist(name: String, songs: List<Song>): ImportedPlaylist
}
