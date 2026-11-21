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

package me.knighthat.kreate3.playlist

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.youTubeSyncSetting
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/**
 * A playlist made on YouTube Music that its library doesn't list yet, see [CreatedPlaylists].
 *
 * @property key tells it from the others, also from one with the same [name]
 * @property id its id on YouTube
 */
data class CreatedPlaylist(val key: Long, val id: String, val name: String)

/**
 * Playlists just made on YouTube Music: its library lists a new playlist only a moment later, so
 * the Playlists tab shows a placeholder for each until it does. The library is checked every
 * second, as many times as `Preferences.NETWORK_MAX_RETRIES` says; the placeholder goes once the
 * playlist is listed or the checks ran out, and [settled] tells the tab to fetch its list again.
 * A playlist deleted outside its page tells the tab the same way, see [listChanged].
 *
 * @param scope outlives the screen that made the playlist
 */
class CreatedPlaylists(
    private val youTube: YouTubeProvider,
    private val scope: CoroutineScope,
) {

    private val nextKey = AtomicLong()
    private val _waiting = MutableStateFlow(emptyList<CreatedPlaylist>())
    private val _settled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The playlists made that YouTube doesn't list yet, newest first. */
    val waiting: StateFlow<List<CreatedPlaylist>> = _waiting.asStateFlow()

    /**
     * Emits each time a playlist leaves [waiting] or [listChanged] is called: the list of
     * YouTube's or the server's playlists changed.
     */
    val settled: SharedFlow<Unit> = _settled.asSharedFlow()

    /** Tells the Playlists tab to fetch its list again, like after a playlist was deleted. */
    fun listChanged() {
        _settled.tryEmit(Unit)
    }

    /**
     * Shows the playlist [id] called [name], just made on YouTube, until its library lists it.
     * Nothing shows while the Playlists tab leaves YouTube's playlists out.
     */
    fun add(id: String, name: String) {
        if (!LibrarySection.PLAYLISTS.youTubeSyncSetting.value) return

        val playlist = CreatedPlaylist(nextKey.getAndIncrement(), id, name)
        _waiting.update { listOf(playlist) + it }
        scope.launch {
            try {
                awaitListed(id)
            } finally {
                _waiting.update { list -> list.filterNot { it.key == playlist.key } }
                _settled.tryEmit(Unit)
            }
        }
    }

    private suspend fun awaitListed(id: String) {
        repeat(Preferences.NETWORK_MAX_RETRIES.value) {
            delay(CHECK_GAP)
            val listed = try {
                youTube.libraryPlaylists()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't check whether YouTube lists playlist $id" }
                null
            } ?: return
            if (listed.any { it.remoteId == id }) return
        }
    }

    private companion object {
        const val TAG = "CreatedPlaylists"
        val CHECK_GAP = 1.seconds
    }
}
