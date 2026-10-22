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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalPlaylist

private const val TAG = "LocalPlaylistDetail"

/**
 * The playlist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 * Its songs can be moved and taken out, see [edit].
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

    private val _editMode = MutableStateFlow(CoverEditMode.NONE)

    /** What the covers of [songs] do, see [edit]. */
    val editMode = _editMode.asStateFlow()

    private val _arranged = MutableStateFlow<List<MediaItem>?>(null)

    /**
     * Every song of the playlist in its order while [editMode] is [CoverEditMode.REORDER], `null`
     * otherwise and while they load. A page of [songs] can't be dragged around, its songs move
     * in this list instead.
     */
    val arranged = _arranged.asStateFlow()

    private var arranging: Job? = null

    // A move is relative to another song, they are made in the order the user made them
    private val moves = Mutex()

    /** Every song of [songs] right now, in its order, for the quick actions. */
    suspend fun allSongs(): List<MediaItem> =
        // Every song of a large library takes long to turn into items, never on the main thread
        withContext(Dispatchers.Default) {
            songRepository.listOfPlaylist(playlistId).mapNotNull { it.toMediaItem() }
        }

    /** Switches [editMode] to [mode], loading [arranged] for [CoverEditMode.REORDER]. */
    fun edit(mode: CoverEditMode) {
        if (mode == _editMode.value) return
        _editMode.value = mode
        arranging?.cancel()
        _arranged.value = null
        if (mode == CoverEditMode.REORDER) arrange()
    }

    private fun arrange() {
        arranging = viewModelScope.launch { _arranged.value = allSongs() }
    }

    /**
     * Moves the song [songId] to right after [after], to the start when it is `null`. [arranged]
     * is expected to show it there already. [onDone] tells whether it worked, when it didn't
     * [arranged] loads again to show the order the playlist kept.
     */
    fun moveSong(songId: String, after: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val moved = try {
                moves.withLock { repository.moveSong(playlistId, songId, after) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't move $songId after $after in playlist $playlistId", e)
                false
            }
            if (!moved && _editMode.value == CoverEditMode.REORDER) {
                arranging?.cancel()
                arrange()
            }
            onDone(moved)
        }
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
