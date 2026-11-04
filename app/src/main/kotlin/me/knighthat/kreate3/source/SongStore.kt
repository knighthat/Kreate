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

package me.knighthat.kreate3.source

import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider

/**
 * Stores a song fetched live once the user keeps something about it, like a reaction or a place
 * in a playlist, which needs the song in the database and the user's library. It is stored like
 * when it starts playing (`PlayedSongRecorder`): asked to its source again, then added to the
 * library. A song queued before is in the catalog already and is only added to the library.
 */
class SongStore(
    private val songs: SongRepository,
    private val catalog: RemoteCatalogRepository,
    private val youTube: YouTubeProvider,
    private val openSubsonic: OpenSubsonicProvider,
) {

    /**
     * Makes sure the song [songId] is in the shared catalog, getting it from [origin] when it
     * isn't, without adding it to the user's library: the saved queue only needs the song.
     *
     * @throws IllegalStateException when it isn't and [origin] can't give it
     */
    suspend fun storeInCatalog(songId: String, origin: Origin) {
        if (songs.find(songId).first() != null) return

        val song = songDetails(origin, songId, youTube, openSubsonic)
            ?: error("$origin doesn't know $songId")
        catalog.saveQueuedSong(song)
    }

    /**
     * Makes sure the song [songId] is in the database and the user's library, getting it from [origin] when it isn't.
     *
     * @throws IllegalStateException when it isn't and [origin] can't give it
     */
    suspend fun store(songId: String, origin: Origin?) {
        val remote = origin?.takeIf { it != Origin.LOCAL }
        if (remote == null) {
            if (songs.find(songId).first() != null) return
            error("$songId isn't in the database")
        }
        // A song only queued so far is in the catalog already, it only joins the library
        if (catalog.addToLibrary(songId)) return

        val song = songDetails(remote, songId, youTube, openSubsonic)
            ?: error("$remote doesn't know $songId")
        catalog.savePlayedSong(song)
    }
}
