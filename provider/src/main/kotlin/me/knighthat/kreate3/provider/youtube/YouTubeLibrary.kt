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
package me.knighthat.kreate3.provider.youtube

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.LibrarySection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Keeps the songs the signed in user liked on YouTube liked in their library. Liked songs are the
 * only part of the YouTube library that is stored, its albums, artists and playlists are fetched
 * each time they show. Signing out, or turning the account or the syncing off, only stops the
 * syncing, the likes stay as they are.
 *
 * @param syncs whether the liked songs are synced, [Preferences.YOUTUBE_SYNC_SONGS]
 */
class YouTubeLibrary(
    private val provider: YouTubeProvider,
    private val repository: RemoteCatalogRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val syncs: () -> Boolean = { LibrarySection.SONGS.youTubeSyncSetting.value },
) {

    /** When the liked songs of each session were last brought up to date, or are being now. */
    private val refreshed = ConcurrentHashMap<String, Instant>()

    /**
     * Brings the liked songs up to date unless it was done recently or their syncing is off. A
     * failure, like a session that ended, leaves what is stored as it was and lets the next call
     * try again.
     */
    fun refresh() {
        scope.launch {
            if (!syncs()) return@launch
            val session = provider.sessionId() ?: return@launch
            val now = clock.now()

            // Claims the session, so a second call while this one runs does nothing
            val last = refreshed[session]
            if (last != null && now - last < MIN_INTERVAL) return@launch
            if (last == null && refreshed.putIfAbsent(session, now) != null) return@launch
            if (last != null && !refreshed.replace(session, last, now)) return@launch

            try {
                if (!sync(session)) refreshed.remove(session)
            } catch (e: CancellationException) {
                refreshed.remove(session)
                throw e
            } catch (e: Exception) {
                refreshed.remove(session)
                Log.w(TAG, "Couldn't bring the liked songs of YouTube up to date", e)
            }
        }
    }

    /** Brings the liked songs up to date now, like right after signing in. */
    fun refreshNow() {
        refreshed.clear()
        refresh()
    }

    /**
     * @return whether the songs were stored, `false` when nobody is signed in, or the session
     * changed or the syncing was turned off meanwhile
     */
    private suspend fun sync(session: String): Boolean {
        val liked = provider.likedSongs() ?: return false
        if (provider.sessionId() != session || !syncs()) return false
        // More are liked than were fetched, the likes not fetched are left as they are then
        repository.syncLikedSongs(
            Origin.YOUTUBE,
            liked.items,
            complete = liked.continuation == null,
        )
        return true
    }

    private companion object {

        const val TAG = "YouTubeLibrary"

        val MIN_INTERVAL = 5.minutes
    }
}

/**
 * The setting that turns [this] section of the YouTube library on: the liked songs are synced,
 * the others show on the home screen.
 */
val LibrarySection.youTubeSyncSetting
    get() = when (this) {
        LibrarySection.SONGS -> Preferences.YOUTUBE_SYNC_SONGS
        LibrarySection.ALBUMS -> Preferences.YOUTUBE_SYNC_ALBUMS
        LibrarySection.ARTISTS -> Preferences.YOUTUBE_SYNC_ARTISTS
        LibrarySection.PLAYLISTS -> Preferences.YOUTUBE_SYNC_PLAYLISTS
    }
