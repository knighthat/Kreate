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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.source.PickedCovers
import me.knighthat.kreate3.ui.navigation.LocalPlaylist

private const val TAG = "EditPlaylist"

/**
 * What the user sees and changes on the edit screen.
 *
 * @property name the name, not trimmed yet
 * @property thumbnail the image picked from the device, or the copy the playlist has, `null` for
 * none
 */
data class PlaylistDraft(
    val name: String = "",
    val pinned: Boolean = false,
    val thumbnail: Uri? = null,
) {

    /** Whether it can be applied. */
    val valid: Boolean get() = name.isNotBlank()

    /** What applying it would store, so changing nothing that shows is no change. */
    internal val result: Triple<String, Boolean, Uri?>
        get() = Triple(name.trim(), pinned, thumbnail)
}

/**
 * @property baseline what is stored
 * @property draft what the user made of it, only in memory until applied
 * @property saving whether applying is under way, nothing can change meanwhile
 */
data class EditPlaylistState(
    val baseline: PlaylistDraft,
    val draft: PlaylistDraft,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = draft.result != baseline.result
}

/**
 * Edits the name, picture and pin of the playlist whose id is in the saved state. The changes stay
 * in [state] until [apply] stores them. A picture from the device is copied into the profile's
 * [CoverFiles.custom] folder first and the playlist keeps the copy, so it doesn't depend on the
 * original.
 */
class EditPlaylistViewModel(
    savedStateHandle: SavedStateHandle,
    private val playlists: PlaylistRepository,
    private val pictures: PickedCovers,
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle[LocalPlaylist.ID]) {
        "LocalPlaylist.ID is missing from the saved state"
    }

    private val _state = MutableStateFlow<EditPlaylistState?>(null)

    /** `null` while the playlist loads, or when it doesn't exist. */
    val state: StateFlow<EditPlaylistState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = playlists.find(playlistId).first() ?: return@launch
            stored.toDraft().let { _state.value = EditPlaylistState(it, it) }
        }
    }

    /** Changes the draft. */
    fun edit(change: (PlaylistDraft) -> PlaylistDraft) {
        _state.update { if (it == null || it.saving) it else it.copy(draft = change(it.draft)) }
    }

    /** Throws the changes away, back to what is stored. */
    fun reset() {
        _state.update { if (it == null || it.saving) it else it.copy(draft = it.baseline) }
    }

    /** Stores the draft, [onDone] tells whether it worked. The draft stays as it is on failure. */
    fun apply(onDone: (Boolean) -> Unit) {
        val current = _state.value ?: return
        if (current.saving || !current.draft.valid) return

        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            val stored = try {
                write(current.draft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't save playlist $playlistId" }
                null
            }
            _state.update {
                if (stored != null) EditPlaylistState(stored, stored) else it?.copy(saving = false)
            }
            onDone(stored != null)
        }
    }

    /** @return the draft as it is stored now */
    private suspend fun write(draft: PlaylistDraft): PlaylistDraft {
        val stored = checkNotNull(playlists.find(playlistId).first()) { "Playlist is gone" }
        val owner = PickedCovers.ownerOf(playlistId)

        // A picture from the device is copied, one already stored stays as it is
        val thumbnail = pictures.keep(owner, draft.thumbnail)
        val name = draft.name.trim()
        try {
            playlists.save(stored.copy(name = name, thumbnail = thumbnail, isPinned = draft.pinned))
        } catch (e: Exception) {
            if (thumbnail != draft.thumbnail) pictures.discard(owner, thumbnail, null)
            throw e
        }
        // The earlier copy has no use once the playlist points elsewhere
        pictures.discard(owner, stored.thumbnail, thumbnail)

        return draft.copy(name = name, thumbnail = thumbnail)
    }

    private fun UserPlaylist.toDraft() = PlaylistDraft(name, isPinned, thumbnail)
}
