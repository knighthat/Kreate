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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItems
import me.knighthat.kreate3.ui.navigation.LocalArtist
import kotlin.time.Clock

/**
 * The artist whose id is in the saved state, `null` while it loads or when it doesn't exist, and its songs.
 *
 * @param scope the app's, where a follow is saved
 */
class LocalArtistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: ArtistRepository,
    private val songRepository: UserSongRepository,
    albumRepository: UserAlbumRepository,
    private val userArtists: UserArtistRepository,
    private val scope: CoroutineScope,
) : ViewModel() {

    val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    val artist = repository.find(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums = albumRepository.ofArtist(artistId, ALBUM_LIMIT)
        .map { albums -> albums.map { it.toMediaItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs = songRepository.ofArtist(artistId)
        .toMediaItems()
        .cachedIn(viewModelScope)

    /** Whether the user follows the artist, `null` until that is known. */
    val isFollowed: StateFlow<Boolean?> = userArtists.find(artistId)
        .map { it?.followSince.isPositive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every song of [songs] right now, in its order, for the quick actions. */
    suspend fun allSongs(): List<MediaItem> =
        // Every song of a large library takes long to turn into items, never on the main thread
        withContext(Dispatchers.Default) {
            songRepository.listOfArtist(artistId).mapNotNull { it.toMediaItem() }
        }

    /** Follows the artist, or unfollows them, nothing until [isFollowed] is known. */
    fun toggleFollow() {
        val on = !(isFollowed.value ?: return)
        scope.launch {
            try {
                userArtists.update(artistId) {
                    it.copy(followSince = if (on) Clock.System.now() else null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the follow of $artistId", e)
            }
        }
    }

    private companion object {
        const val TAG = "LocalArtistDetail"

        /** Albums shown on the page, the artist may have more. */
        const val ALBUM_LIMIT = 10
    }
}
