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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.PlaylistPrivacy
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.ui.navigation.EDIT_ITEM_ID
import me.knighthat.kreate3.ui.navigation.EDIT_ITEM_ORIGIN

private const val TAG = "EditOnlinePlaylist"

/**
 * What the user edits of a playlist of a source.
 *
 * @property name the name, not trimmed yet
 * @property description what the playlist is about (OpenSubsonic's comment), not trimmed yet
 * @property privacy who can see it, `null` while YouTube didn't say and nothing is picked. The
 * OpenSubsonic server only knows [PlaylistPrivacy.PUBLIC] (every user) and
 * [PlaylistPrivacy.PRIVATE].
 * @property thumbnail the playlist's picture, shown but never changed: neither source's API
 * takes one (Navidrome's own does, but the maintainer chose not to use it)
 */
data class OnlineDraft(
    val name: String = "",
    val description: String = "",
    val privacy: PlaylistPrivacy? = null,
    val thumbnail: Uri? = null,
) {
    /** What applying it would store, so changing nothing that shows is no change. */
    internal val result: OnlineDraft
        get() = copy(name = name.trim(), description = description.trim())
}

/**
 * @property saved what the source has
 * @property draft what the user made of it, only in memory until applied
 * @property saving whether applying is under way, nothing can change meanwhile
 */
data class EditOnlinePlaylistState(
    val saved: OnlineDraft,
    val draft: OnlineDraft,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = draft.result != saved.result
    val valid: Boolean get() = draft.name.isNotBlank()
}

/**
 * Edits the name, description and visibility of the playlist of YouTube Music or of the
 * OpenSubsonic server in the saved state (an id and its [Origin]), which the user may change.
 * Nothing is stored on the device: [apply] sends what changed to the source, the playlist is
 * fetched when the screen opens.
 */
class EditOnlinePlaylistViewModel(
    savedStateHandle: SavedStateHandle,
    private val youTube: YouTubeProvider,
    private val openSubsonic: OpenSubsonicProvider,
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle[EDIT_ITEM_ID]) {
        "EDIT_ITEM_ID is missing from the saved state"
    }

    val origin: Origin = Origin.valueOf(
        checkNotNull(savedStateHandle[EDIT_ITEM_ORIGIN]) {
            "EDIT_ITEM_ORIGIN is missing from the saved state"
        }
    )

    private val _state = MutableStateFlow<EditOnlinePlaylistState?>(null)

    /** `null` while the playlist loads, or when it couldn't be. */
    val state: StateFlow<EditOnlinePlaylistState?> = _state.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** Whether the playlist couldn't be fetched or isn't the user's to change. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't load playlist $playlistId of $origin" }
                null
            }
            if (loaded == null)
                _failed.value = true
            else
                _state.value = EditOnlinePlaylistState(loaded, loaded)
        }
    }

    /** What the playlist has now. */
    private suspend fun load(): OnlineDraft? = when (origin) {
        Origin.YOUTUBE -> youTube.playlistPage(playlistId)?.takeIf { it.editable }?.let {
            OnlineDraft(
                it.playlist.name,
                it.description.orEmpty(),
                it.privacy,
                it.playlist.thumbnail
            )
        }

        Origin.OPEN_SUBSONIC -> openSubsonic.playlistWithSongs(playlistId)?.takeIf { it.editable }
            ?.let {
                OnlineDraft(
                    it.playlist.name,
                    it.comment,
                    if (it.public) PlaylistPrivacy.PUBLIC else PlaylistPrivacy.PRIVATE,
                    it.playlist.thumbnail,
                )
            }

        Origin.LOCAL -> null
    }

    /** Changes the draft. */
    fun edit(change: (OnlineDraft) -> OnlineDraft) {
        _state.update { if (it == null || it.saving) it else it.copy(draft = change(it.draft)) }
    }

    /** Throws the changes away, back to what the source has. */
    fun reset() {
        _state.update { if (it == null || it.saving) it else it.copy(draft = it.saved) }
    }

    /** Sends the draft, [onDone] tells whether it worked. The draft stays as it is on failure. */
    fun apply(onDone: (Boolean) -> Unit) {
        val current = _state.value ?: return
        if (current.saving || !current.valid) return

        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            val sent = try {
                send(current.saved, current.draft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't change playlist $playlistId of $origin" }
                false
            }
            _state.update {
                when {
                    it == null -> null
                    sent -> {
                        val stored = current.draft.copy(
                            name = current.draft.name.trim(),
                            description = current.draft.description.trim(),
                        )
                        it.copy(saved = stored, draft = stored, saving = false)
                    }

                    else -> it.copy(saving = false)
                }
            }
            onDone(sent)
        }
    }

    /** Sends what [draft] changed of [saved]. */
    private suspend fun send(saved: OnlineDraft, draft: OnlineDraft): Boolean {
        val name = draft.name.trim().takeIf { it != saved.name.trim() }
        val description = draft.description.trim().takeIf { it != saved.description.trim() }
        val privacy = draft.privacy.takeIf { it != saved.privacy }
        return when (origin) {
            Origin.YOUTUBE -> {
                youTube.updatePlaylist(playlistId, name, description, privacy)
                true
            }

            Origin.OPEN_SUBSONIC -> openSubsonic.updatePlaylistDetails(
                playlistId,
                name,
                description,
                privacy?.let { it == PlaylistPrivacy.PUBLIC },
            )

            Origin.LOCAL -> false
        }
    }
}
