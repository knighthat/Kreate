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

import androidx.media3.common.MediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.ui.component.CoverEditMode

private const val TAG = "PlaylistEdit"

/**
 * What the user changed in a playlist of a server, for [PlaylistEdit.save] to send. The playlist
 * is as the page showed it when the editing began.
 *
 * @property original the ids of the songs shown then, in order
 * @property order the ids of the songs left, in the order the user gave them
 * @property hidden ids of songs the playlist may hold but the page didn't show, the user disliked
 * them
 */
class PlaylistChanges(
    val original: List<String>,
    val order: List<String>,
    val hidden: Set<String>,
) {

    /** Ids of the songs the user took out. */
    val removed: Set<String> = original.toSet() - order.toSet()

    /** Whether the songs left are in the order they had. */
    val onlyRemoved: Boolean = order == original.filter { it !in removed }
}

/** How [PlaylistEdit.save] went. */
sealed interface SaveResult {

    data object Saved : SaveResult

    /** The playlist on the server is not what the page showed, nothing was sent. */
    data object Changed : SaveResult

    /** Whatever went wrong, [reason] says it in words for the user. */
    data class Failed(val reason: String) : SaveResult
}

/**
 * Editing the songs of a playlist of a server on its page: they are moved and taken out here
 * first, and the server hears about it once, in [save]. Used by the view model of a page.
 *
 * @param scope outlives the page, [save] and [delete] go on when the page is left
 * @param shown the songs on the page, the server's, `null` while they aren't there
 * @param hidden ids of songs the user disliked, which the page leaves out
 */
class PlaylistEdit(
    private val scope: CoroutineScope,
    private val shown: () -> List<MediaItem>?,
    private val hidden: () -> Set<String>,
) {

    private val _arranging = MutableStateFlow<List<MediaItem>?>(null)

    /**
     * The songs as the user left them while the playlist is edited, `null` otherwise: moved, and
     * the ones taken out are missing. Nothing is sent before [save].
     */
    val arranging: StateFlow<List<MediaItem>?> = _arranging.asStateFlow()

    private val _mode = MutableStateFlow(CoverEditMode.NONE)

    /** What the covers do while [arranging]: handles to move, or buttons to take out. */
    val mode: StateFlow<CoverEditMode> = _mode.asStateFlow()

    private val _saving = MutableStateFlow(false)

    /** Whether [save] is waiting for the server. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _deleting = MutableStateFlow(false)

    /** Whether [delete] is waiting for the server. */
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _confirmingDelete = MutableStateFlow(false)

    /**
     * Whether the dialog that asks to delete the playlist shows. It stays until the server has
     * answered, see [delete].
     */
    val confirmingDelete: StateFlow<Boolean> = _confirmingDelete.asStateFlow()

    // What the page showed when the editing began
    private var original: List<String> = emptyList()
    private var hiddenThen: Set<String> = emptySet()

    /** Whether the songs differ from the server's, in their order or by some taken out. */
    fun hasChanges(): Boolean =
        _arranging.value?.map { it.mediaId }.let { it != null && it != original }

    /**
     * Switches what the covers do to [mode], starting from the songs on the page when nothing is
     * being edited. Nothing is sent: turning the editing off is [save].
     */
    fun start(mode: CoverEditMode) {
        if (mode == CoverEditMode.NONE || _saving.value) return
        if (_arranging.value == null) {
            val songs = shown() ?: return
            original = songs.map { it.mediaId }
            hiddenThen = hidden()
            _arranging.value = songs
        }
        _mode.value = mode
    }

    /** The songs as the user changed them, [songs] are some of [arranging], in any order. */
    fun arrange(songs: List<MediaItem>) {
        if (_arranging.value != null && !_saving.value) _arranging.value = songs
    }

    /** Throws the changes away, the server is not asked. */
    fun discard() {
        if (_saving.value) return
        _arranging.value = null
        _mode.value = CoverEditMode.NONE
    }

    /**
     * Sends the changes with [commit], on the app's scope. Editing ends either way.
     *
     * @param commit sends them and tells how it went
     * @param applied on the main thread, before editing ends: what to change on the page, gets
     * the result and the songs as the user left them
     * @param onDone on the main thread, after editing ended
     */
    fun save(
        commit: suspend (PlaylistChanges) -> SaveResult,
        applied: (SaveResult, List<MediaItem>) -> Unit,
        onDone: (SaveResult) -> Unit,
    ) {
        val songs = _arranging.value ?: return
        if (_saving.value) return
        val changes = PlaylistChanges(original, songs.map { it.mediaId }, hiddenThen)
        if (changes.order == changes.original) {
            discard()
            return
        }
        _saving.value = true
        scope.launch {
            val result = try {
                commit(changes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Whatever goes wrong must not leave the page waiting for the server
                Logger.w(e, TAG) { "Couldn't save the changes of a playlist" }
                SaveResult.Failed(e.message.orEmpty())
            }
            withContext(Dispatchers.Main) {
                applied(result, songs)
                _arranging.value = null
                _mode.value = CoverEditMode.NONE
                _saving.value = false
                onDone(result)
            }
        }
    }

    /** Shows the dialog that asks to delete the playlist. */
    fun askDelete() {
        _confirmingDelete.value = true
    }

    /** Closes the dialog without deleting, not while the server is deleting. */
    fun cancelDelete() {
        if (!_deleting.value) _confirmingDelete.value = false
    }

    /**
     * Deletes the playlist with [call], on the app's scope. Ignored while one is under way or the
     * changes are being sent.
     *
     * @param call asks the server, and fails when it can't
     * @param onDone on the main thread, with whether it worked
     */
    fun delete(call: suspend () -> Unit, onDone: (Boolean) -> Unit) {
        if (_deleting.value || _saving.value) return
        _deleting.value = true
        scope.launch {
            val deleted = try {
                call()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't delete a playlist" }
                false
            }
            withContext(Dispatchers.Main) {
                _deleting.value = false
                _confirmingDelete.value = false
                onDone(deleted)
            }
        }
    }
}
