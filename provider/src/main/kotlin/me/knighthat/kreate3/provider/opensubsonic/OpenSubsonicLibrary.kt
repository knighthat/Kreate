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
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.ReactionSender
import me.knighthat.kreate3.provider.isLike
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Keeps the songs starred on the server liked in the active user's library, and the likes given
 * in the app starred on the server. Starred songs are the only part of the server's library that
 * is stored, its albums, artists and playlists are fetched each time they show. Turning the server
 * or the syncing off only stops the syncing, the likes stay as they are.
 *
 * @param syncs whether the starred songs are synced, [Preferences.OPEN_SUBSONIC_SYNC_SONGS]
 */
class OpenSubsonicLibrary(
    private val provider: OpenSubsonicProvider,
    private val repository: RemoteCatalogRepository,
    private val userSongs: UserSongRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val syncs: () -> Boolean = { Preferences.OPEN_SUBSONIC_SYNC_SONGS.value },
) {

    /** When the liked songs of each server address were last brought up to date, or are being now. */
    private val refreshed = ConcurrentHashMap<String, Instant>()

    private val sender = ReactionSender(userSongs)

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
     * Changes the user's reaction to [songId], a song of the server, to what [likedAt] makes of
     * the one stored, see `UserSong.likedAt`. While the syncing is on, a song that becomes liked
     * is starred on the server and one that stops being liked loses its star. The API has no
     * dislike, so a dislike stays on the device: a starred song that gets one only loses its star.
     *
     * When the server can't be told, the reaction is taken back to what it was, unless another
     * one replaced it meanwhile.
     *
     * @throws ServerException when the server can't be used or refuses the star, the reaction
     * is taken back then
     */
    suspend fun react(songId: String, likedAt: (Instant?) -> Instant?) =
        sender.react(songId, likedAt) { before, after ->
            val liked = after.isLike
            if (before.isLike != liked && syncs()) provider.setStarred(songId, liked)
        }

    /**
     * @return whether the songs were stored, `false` when the server is off, or its address
     * changed, the syncing was turned off or a reaction was saved or sent meanwhile
     */
    private suspend fun sync(server: String): Boolean {
        val since = sender.mark()
        val songs = provider.starredSongs() ?: return false

        return sender.store(since) {
            if (provider.serverUrl != server || !syncs()) return@store false
            repository.syncLikedSongs(Origin.OPEN_SUBSONIC, songs)
            true
        }
    }

    private companion object {

        const val TAG = "OpenSubsonicLibrary"

        val MIN_INTERVAL = 5.minutes
    }
}
