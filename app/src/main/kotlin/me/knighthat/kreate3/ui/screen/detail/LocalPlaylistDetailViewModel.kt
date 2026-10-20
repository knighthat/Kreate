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

package me.knighthat.kreate3.ui.screen.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalPlaylist

private const val TAG = "LocalPlaylistDetail"

/**
 * The playlist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 */
class LocalPlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val songRepository: UserSongRepository,
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle[LocalPlaylist.ID]) {
        "LocalPlaylist.ID is missing from the saved state"
    }

    val playlist = repository.find(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs = songRepository.ofPlaylist(playlistId)
        .toMediaItems()
        .cachedIn(viewModelScope)

    /** Every song of [songs] right now, in its order, for the quick actions. */
    suspend fun allSongs(): List<MediaItem> =
        // Every song of a large library takes long to turn into items, never on the main thread
        withContext(Dispatchers.Default) {
            songRepository.listOfPlaylist(playlistId).mapNotNull { it.toMediaItem() }
        }

    /** Takes the song [songId] out of the playlist, [onDone] tells whether it worked. */
    fun removeSong(songId: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val removed = try {
                repository.removeSong(playlistId, songId)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove $songId from playlist $playlistId", e)
                false
            }
            onDone(removed)
        }
    }
}
