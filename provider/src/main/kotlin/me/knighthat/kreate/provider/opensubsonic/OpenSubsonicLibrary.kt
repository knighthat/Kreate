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

package me.knighthat.kreate.provider.opensubsonic

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.repository.RemoteCatalogRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A part of the library that is listed on its own.
 */
enum class LibrarySection {
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
}

/**
 * Brings the server's library into the database, one [LibrarySection] at a time, so its items
 * show among the others, and the songs of an album, artist or playlist when it is opened.
 * Nothing happens while the server is off or not set up.
 *
 * A section is fetched again at most every few minutes, opening a tab back and forth doesn't
 * download the library each time. The work runs on [scope], the app's long lived scope, so
 * leaving the screen doesn't throw away a library already downloaded.
 */
class OpenSubsonicLibrary(
    private val provider: OpenSubsonicProvider,
    private val repository: RemoteCatalogRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {

    /** When each section of each server address was last brought up to date, or is being now. */
    private val refreshed = ConcurrentHashMap<Pair<String, LibrarySection>, Instant>()

    /**
     * Brings [section] up to date unless it was done recently. A failure leaves what is stored
     * as it was, and lets the next call try again.
     */
    fun refresh(section: LibrarySection) {
        val server = provider.serverUrl ?: return
        val key = server to section
        val now = clock.now()

        // Claims the section, so a second call while this one runs does nothing
        val last = refreshed[key]
        if (last != null && now - last < MIN_INTERVAL) return
        if (last == null && refreshed.putIfAbsent(key, now) != null) return
        if (last != null && !refreshed.replace(key, last, now)) return

        scope.launch {
            try {
                if (!sync(server, section)) refreshed.remove(key)
            } catch (e: CancellationException) {
                refreshed.remove(key)
                throw e
            } catch (e: Exception) {
                refreshed.remove(key)
                Log.w(TAG, "Couldn't bring $section of the server up to date", e)
            }
        }
    }

    /**
     * Stores the album and every song on it.
     *
     * @return `false` when the server is off or not set up, nothing is stored then
     * @throws ServerException when the server can't be used or refuses
     */
    suspend fun syncAlbum(albumId: String): Boolean {
        val (album, songs) = provider.albumWithSongs(albumId) ?: return false
        repository.syncSongs(Origin.OPEN_SUBSONIC, songs, albums = listOf(album))
        return true
    }

    /**
     * Stores the artist, their albums and every song on those albums.
     *
     * @return `false` when the server is off or not set up, nothing is stored then
     * @throws ServerException when the server can't be used or refuses
     */
    suspend fun syncArtist(artistId: String): Boolean {
        val found = provider.artistWithSongs(artistId) ?: return false
        repository.syncSongs(
            Origin.OPEN_SUBSONIC,
            found.songs,
            albums = found.albums,
            artists = listOf(found.artist),
        )
        return true
    }

    /**
     * Stores every song of a playlist and makes them the songs of its row in the database.
     *
     * @param playlistId id of the playlist in the database
     * @param remoteId id of the playlist on the server
     * @return `false` when the server is off or not set up, nothing is stored then
     * @throws ServerException when the server can't be used or refuses
     */
    suspend fun syncPlaylist(playlistId: Long, remoteId: String): Boolean {
        val songs = provider.playlistSongs(remoteId) ?: return false
        repository.syncSongs(Origin.OPEN_SUBSONIC, songs, playlistId = playlistId)
        return true
    }

    /**
     * @return whether the section was stored, `false` when the server is off or its address
     * changed meanwhile
     */
    private suspend fun sync(server: String, section: LibrarySection): Boolean {
        when (section) {
            LibrarySection.ALBUMS -> {
                val albums = provider.libraryAlbums() ?: return false
                if (provider.serverUrl != server) return false
                repository.syncAlbums(Origin.OPEN_SUBSONIC, albums)
            }

            LibrarySection.ARTISTS -> {
                val artists = provider.libraryArtists() ?: return false
                if (provider.serverUrl != server) return false
                repository.syncArtists(Origin.OPEN_SUBSONIC, artists)
            }

            LibrarySection.PLAYLISTS -> {
                val playlists = provider.libraryPlaylists() ?: return false
                if (provider.serverUrl != server) return false
                repository.syncPlaylists(Origin.OPEN_SUBSONIC, playlists)
            }
        }
        return true
    }

    private companion object {

        const val TAG = "OpenSubsonicLibrary"

        val MIN_INTERVAL = 5.minutes
    }
}
