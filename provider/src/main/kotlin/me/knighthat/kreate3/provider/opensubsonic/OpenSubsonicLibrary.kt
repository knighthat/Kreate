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

package me.knighthat.kreate3.provider.opensubsonic

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.LibrarySection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Keeps the songs starred on the server liked in the active user's library. Starred songs are
 * the only part of the server's library that is stored, its albums, artists and playlists are
 * fetched each time they show. Turning the server or the syncing off only stops the syncing, the
 * likes stay as they are.
 *
 * @param syncs whether the starred songs are synced, [Preferences.OPEN_SUBSONIC_SYNC_SONGS]
 */
class OpenSubsonicLibrary(
    private val provider: OpenSubsonicProvider,
    private val repository: RemoteCatalogRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val syncs: () -> Boolean = { LibrarySection.SONGS.openSubsonicSyncSetting.value },
) {

    /** When the liked songs of each server address were last brought up to date, or are being now. */
    private val refreshed = ConcurrentHashMap<String, Instant>()

    /**
     * Brings the liked songs up to date unless it was done recently or their syncing is off. A
     * failure leaves what is stored as it was, and lets the next call try again.
     */
    fun refresh() {
        if (!syncs()) return
        val server = provider.serverUrl ?: return
        val now = clock.now()

        // Claims the server, so a second call while this one runs does nothing
        val last = refreshed[server]
        if (last != null && now - last < MIN_INTERVAL) return
        if (last == null && refreshed.putIfAbsent(server, now) != null) return
        if (last != null && !refreshed.replace(server, last, now)) return

        scope.launch {
            try {
                if (!sync(server)) refreshed.remove(server)
            } catch (e: CancellationException) {
                refreshed.remove(server)
                throw e
            } catch (e: Exception) {
                refreshed.remove(server)
                Log.w(TAG, "Couldn't bring the liked songs of the server up to date", e)
            }
        }
    }

    /** Brings the liked songs up to date now, like right after turning their syncing on. */
    fun refreshNow() {
        refreshed.clear()
        refresh()
    }

    /**
     * @return whether the songs were stored, `false` when the server is off, or its address
     * changed or the syncing was turned off meanwhile
     */
    private suspend fun sync(server: String): Boolean {
        val songs = provider.starredSongs() ?: return false
        if (provider.serverUrl != server || !syncs()) return false
        repository.syncLikedSongs(Origin.OPEN_SUBSONIC, songs)
        return true
    }

    private companion object {

        const val TAG = "OpenSubsonicLibrary"

        val MIN_INTERVAL = 5.minutes
    }
}

/**
 * The setting that turns [this] section of the server's library on: the starred songs are synced,
 * the others show on the home screen.
 */
val LibrarySection.openSubsonicSyncSetting
    get() = when (this) {
        LibrarySection.SONGS -> Preferences.OPEN_SUBSONIC_SYNC_SONGS
        LibrarySection.ALBUMS -> Preferences.OPEN_SUBSONIC_SYNC_ALBUMS
        LibrarySection.ARTISTS -> Preferences.OPEN_SUBSONIC_SYNC_ARTISTS
        LibrarySection.PLAYLISTS -> Preferences.OPEN_SUBSONIC_SYNC_PLAYLISTS
    }
