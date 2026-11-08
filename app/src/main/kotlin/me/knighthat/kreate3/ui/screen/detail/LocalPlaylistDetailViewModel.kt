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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.source.PickedCovers
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalPlaylist

private const val TAG = "LocalPlaylistDetail"

/**
 * What the header of a playlist's page says about its songs.
 *
 * @property length how long they are together, in milliseconds
 * @property covers the covers of its first songs, see [songCovers]
 */
data class PlaylistOverview(val count: Int, val length: Long, val covers: List<Any>)

/**
 * The playlist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 * Its songs can be moved and taken out, see [edit].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val songRepository: UserSongRepository,
    private val scope: CoroutineScope,
    private val pictures: PickedCovers,
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle[LocalPlaylist.ID]) {
        "LocalPlaylist.ID is missing from the saved state"
    }

    val playlist = repository.find(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editMode = MutableStateFlow(CoverEditMode.NONE)

    /** What the covers of [songs] do, see [edit]. */
    val editMode = _editMode.asStateFlow()

    /**
     * Whether [songs] run from the playlist's end, as [Preferences.PLAYLIST_REVERSED] says. Never
     * while [editMode] is on, edits are made on the playlist's own order.
     */
    val reversed = combine(Preferences.PLAYLIST_REVERSED.state, _editMode) { reversed, mode ->
        reversed && mode == CoverEditMode.NONE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences.PLAYLIST_REVERSED.value)

    val songs = reversed
        .flatMapLatest { songRepository.ofPlaylist(playlistId, reversed = it) }
        .toMediaItems()
        .cachedIn(viewModelScope)

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
    suspend fun allSongs(): List<MediaItem> = listed(reversed.value)

    /** What the header says about the songs right now, whichever way [songs] run. */
    suspend fun overview(providers: Providers): PlaylistOverview {
        val songs = listed(reversed = false)
        return withContext(Dispatchers.Default) {
            PlaylistOverview(
                count = songs.size,
                length = songs.sumOf { it.mediaMetadata.durationMs ?: 0 },
                covers = songs.songCovers(providers),
            )
        }
    }

    // Every song of a large library takes long to turn into items, never on the main thread
    private suspend fun listed(reversed: Boolean): List<MediaItem> =
        withContext(Dispatchers.Default) {
            songRepository.listOfPlaylist(playlistId, reversed = reversed)
                .mapNotNull { it.toMediaItem() }
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
        arranging = viewModelScope.launch { _arranged.value = listed(reversed = false) }
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
                Logger.w(e, TAG) { "Couldn't move $songId after $after in playlist $playlistId" }
                false
            }
            if (!moved && _editMode.value == CoverEditMode.REORDER) {
                arranging?.cancel()
                arrange()
            }
            onDone(moved)
        }
    }

    /**
     * Takes the song [songId] out of the playlist, [onDone] tells whether it worked and gets how
     * to put it back, `null` when it failed or the song wasn't in it.
     */
    fun removeSong(songId: String, onDone: (removed: Boolean, undo: (() -> Unit)?) -> Unit) {
        viewModelScope.launch {
            val position = try {
                Result.success(repository.removeSong(playlistId, songId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't remove $songId from playlist $playlistId" }
                Result.failure(e)
            }
            onDone(position.isSuccess, position.getOrNull()?.let { { putBack(songId, it) } })
        }
    }

    /**
     * Deletes the playlist on the app's scope, the page is leaving. [onDone] tells whether it
     * worked, on the main thread.
     */
    fun delete(onDone: (Boolean) -> Unit) {
        scope.launch {
            val deleted = try {
                val picture = repository.find(playlistId).first()?.thumbnail
                repository.delete(playlistId)
                // A picture picked for the playlist has no other use
                pictures.discard(PickedCovers.ownerOf(playlistId), picture, null)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't delete playlist $playlistId" }
                false
            }
            withContext(Dispatchers.Main) { onDone(deleted) }
        }
    }

    /**
     * Puts the song [songId] back at [position], where [removeSong] took it from, on the app's
     * scope: the page may be gone by the time Undo is tapped.
     */
    private fun putBack(songId: String, position: Int) {
        scope.launch {
            try {
                repository.addSong(playlistId, songId, position)
                // Being moved, the songs dragged around are a copy
                if (_editMode.value == CoverEditMode.REORDER) viewModelScope.launch {
                    arranging?.cancel()
                    arrange()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't put $songId back in playlist $playlistId" }
            }
        }
    }
}
