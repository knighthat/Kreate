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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.AlbumRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalAlbum
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The album whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 * The user can put its songs in another order, see [reorder], and back, see [resetOrder].
 *
 * @param scope the app's, where a bookmark is saved
 */
class LocalAlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: AlbumRepository,
    private val songRepository: UserSongRepository,
    private val userAlbums: UserAlbumRepository,
    private val scope: CoroutineScope,
) : ViewModel() {

    val albumId: String = checkNotNull(savedStateHandle[LocalAlbum.ID]) {
        "LocalAlbum.ID is missing from the saved state"
    }

    val album = repository.find(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs = songRepository.ofAlbum(albumId)
        .toMediaItems()
        .cachedIn(viewModelScope)

    /** Whether the user bookmarked the album, `null` until that is known. */
    val isBookmarked: StateFlow<Boolean?> = userAlbums.find(albumId)
        .map { it?.bookmarkedAt.isPositive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isReordering = MutableStateFlow(false)

    /** Whether the covers of [songs] are handles to move them, see [reorder]. */
    val isReordering = _isReordering.asStateFlow()

    private val _arranged = MutableStateFlow<List<MediaItem>?>(null)

    /**
     * Every song of the album in its order while [isReordering], `null` otherwise and while they
     * load. A page of [songs] can't be dragged around, its songs move in this list instead.
     */
    val arranged = _arranged.asStateFlow()

    private var arranging: Job? = null

    // A move is relative to another song, they are made in the order the user made them
    private val moves = Mutex()

    /** Every song of [songs] right now, in its order, for the quick actions. */
    suspend fun allSongs(): List<MediaItem> =
        // Every song of a large library takes long to turn into items, never on the main thread
        withContext(Dispatchers.Default) {
            songRepository.listOfAlbum(albumId).mapNotNull { it.toMediaItem() }
        }

    /** Turns [isReordering] on or off, loading [arranged] when on. */
    fun reorder(on: Boolean) {
        if (on == _isReordering.value) return
        _isReordering.value = on
        arranging?.cancel()
        _arranged.value = null
        if (on) arrange()
    }

    private fun arrange() {
        arranging = viewModelScope.launch { _arranged.value = allSongs() }
    }

    /**
     * Moves the song [songId] to right after [after], to the start when it is `null`. [arranged]
     * is expected to show it there already. [onDone] tells whether it worked, when it didn't
     * [arranged] loads again to show the order the album kept.
     */
    fun moveSong(songId: String, after: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val moved = try {
                moves.withLock { songRepository.moveInAlbum(albumId, songId, after) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't move $songId after $after in album $albumId", e)
                false
            }
            if (!moved && _isReordering.value) {
                arranging?.cancel()
                arrange()
            }
            onDone(moved)
        }
    }

    /**
     * Puts the songs back in the album's own order, [onDone] tells whether it worked and gets how
     * to put the user's order back, `null` when it failed or there was none. [arranged] loads
     * again in that order.
     */
    fun resetOrder(onDone: (reset: Boolean, undo: (() -> Unit)?) -> Unit) {
        viewModelScope.launch {
            val positions = try {
                moves.withLock { songRepository.resetAlbumOrder(albumId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't reset the order of album $albumId", e)
                null
            }
            rearrange()
            onDone(
                positions != null,
                positions?.takeIf { it.isNotEmpty() }?.let { { restoreOrder(it) } },
            )
        }
    }

    /**
     * Gives the songs the [positions] [resetOrder] took off, on the app's scope: the page may be
     * gone by the time Undo is tapped.
     */
    private fun restoreOrder(positions: Map<String, Int>) {
        scope.launch {
            try {
                moves.withLock { songRepository.restoreAlbumOrder(albumId, positions) }
                rearrange()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't restore the order of album $albumId", e)
            }
        }
    }

    /** Loads [arranged] again while songs are moved, it's a copy of the order. */
    private fun rearrange() {
        if (!_isReordering.value) return
        arranging?.cancel()
        arrange()
    }

    /** Whether the album can be shared, it has a link. */
    val canShare: StateFlow<Boolean> = album
        .map { it?.url != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The album's link that anyone can open.
     *
     * @return the link, or why there is none
     */
    fun shareLink(): Result<String> = runCatching {
        val album = checkNotNull(album.value) { "The album isn't loaded" }
        checkNotNull(album.url) { "This album has no link" }
    }

    /** Bookmarks the album, or takes the bookmark away, nothing until [isBookmarked] is known. */
    fun toggleBookmark() {
        val on = !(isBookmarked.value ?: return)
        scope.launch {
            try {
                userAlbums.update(albumId) {
                    it.copy(bookmarkedAt = if (on) Clock.System.now() else null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the bookmark of $albumId", e)
            }
        }
    }

    private companion object {
        const val TAG = "LocalAlbumDetail"
    }
}

/** Whether a reaction holds a positive one, not a dislike or nothing, see `UserAlbum`. */
internal val Instant?.isPositive: Boolean
    get() = this != null && toEpochMilliseconds() >= 0
