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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.OpenSubsonicPlaylist

/**
 * A playlist of the OpenSubsonic server as its screen shows it.
 *
 * @property editable whether the user may change or delete it, found when the page opens
 */
data class OpenSubsonicPlaylistDetail(
    val name: String,
    val thumbnail: Uri?,
    val songs: List<MediaItem>,
    val editable: Boolean,
)

private const val TAG = "OpenSubsonicPlaylist"

/**
 * Fetches the playlist whose id on the server is in the saved state, and its songs. Nothing is
 * stored. Its songs can be moved, see [startArranging].
 */
class OpenSubsonicPlaylistDetailViewModel(
    context: Context,
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
    library: UserSongRepository,
    private val scope: CoroutineScope,
) : LiveDetailViewModel<OpenSubsonicPlaylistDetail>(context, library.dislikedIds()) {

    val playlistId: String = checkNotNull(savedStateHandle[OpenSubsonicPlaylist.ID]) {
        "OpenSubsonicPlaylist.ID is missing from the saved state"
    }

    override val notFound: String get() = context.serverOff()

    /** Whether the user may make shares on the server, `false` until that is known. */
    val canShare = provider.canShare(viewModelScope)

    private val _arranging = MutableStateFlow<List<MediaItem>?>(null)

    /**
     * The songs as the user left them while the playlist is being edited, `null` otherwise:
     * moved, and the ones taken out are missing. Nothing goes to the server before [saveChanges].
     */
    val arranging: StateFlow<List<MediaItem>?> = _arranging.asStateFlow()

    private val _editMode = MutableStateFlow(CoverEditMode.NONE)

    /** What the covers do while [arranging]: handles to move, or buttons to take out. */
    val editMode: StateFlow<CoverEditMode> = _editMode.asStateFlow()

    private val _saving = MutableStateFlow(false)

    /** Whether [saveChanges] is waiting for the server. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _confirmingDelete = MutableStateFlow(false)

    /**
     * Whether the dialog that asks to delete the playlist shows. It stays until the server has
     * answered, see [deletePlaylist].
     */
    val confirmingDelete: StateFlow<Boolean> = _confirmingDelete.asStateFlow()

    private val _deleting = MutableStateFlow(false)

    /** Whether [deletePlaylist] is waiting for the server. */
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    // What the server was believed to have when the editing began, and the disliked songs that
    // were not shown then: the playlist is written back with them in their places
    private var original: List<String> = emptyList()
    private var hidden: Set<String> = emptySet()

    init {
        load()
    }

    /** Whether the songs differ from the server's, in their order or by some taken out. */
    fun hasChanges(): Boolean =
        _arranging.value?.map { it.mediaId }.let { it != null && it != original }

    /**
     * Switches what the covers do to [mode], starting an edit from the songs on the screen,
     * which are the server's. Nothing is sent: [CoverEditMode.NONE] is [saveChanges].
     */
    fun edit(mode: CoverEditMode) {
        if (mode == CoverEditMode.NONE || _saving.value) return
        if (_arranging.value == null) {
            val songs = (state.value as? LiveState.Ready)?.value?.songs ?: return
            original = songs.map { it.mediaId }
            hidden = disliked.value
            _arranging.value = songs
        }
        _editMode.value = mode
    }

    /** The songs as the user changed them, [songs] are some of [arranging], in any order. */
    fun arrange(songs: List<MediaItem>) {
        if (_arranging.value != null && !_saving.value) _arranging.value = songs
    }

    /** Throws the changes away, the server is not asked. */
    fun discardChanges() {
        if (_saving.value) return
        _arranging.value = null
        _editMode.value = CoverEditMode.NONE
    }

    /**
     * Sends the changes. Songs only taken out are removed by their id, whatever else changed on
     * the server meanwhile. A new order makes the server take every song out and get them back
     * in order, which only goes ahead while the playlist is what the page showed. A song the
     * user disliked, which the page doesn't show, and a song the playlist has twice keep their
     * places. Runs on the app's scope, the page may be gone meanwhile. Editing ends either way,
     * with the changes when it worked, the server's playlist as it was when it didn't.
     *
     * @param onDone gets whether it worked and what to tell the user, on the main thread
     */
    fun saveChanges(onDone: (saved: Boolean, message: String) -> Unit) {
        val order = _arranging.value?.map { it.mediaId } ?: return
        if (_saving.value) return
        if (order == original) {
            discardChanges()
            return
        }
        _saving.value = true
        scope.launch {
            var saved = false
            var changed = false
            val message = try {
                val done =
                    provider.rearrangePlaylist(playlistId) { current -> place(current, order) }
                when (done) {
                    true -> {
                        saved = true
                        context.getString(R.string.snackbar_playlist_changes_saved)
                    }

                    false -> {
                        changed = true
                        context.getString(R.string.error_playlist_changed_on_server)
                    }

                    null -> context.serverOff()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServerException) {
                Logger.w(e, TAG) { "Couldn't save the changes of playlist $playlistId" }
                context.getString(R.string.error_playlist_save_changes, e.reason(context))
            } catch (e: Exception) {
                // Whatever else goes wrong must not leave the page waiting for the server
                Logger.w(e, TAG) { "Couldn't save the changes of playlist $playlistId" }
                context.getString(R.string.error_playlist_save_changes, e.message.orEmpty())
            }
            withContext(Dispatchers.Main) {
                val songs = _arranging.value
                if (saved && songs != null) amend { copy(songs = songs) }
                // The server's playlist is not what the page showed, it is fetched again
                if (changed) load()
                _arranging.value = null
                _editMode.value = CoverEditMode.NONE
                _saving.value = false
                onDone(saved, message)
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
     * Deletes the playlist from the server, on the app's scope: the page may be leaving.
     * [onDone] gets whether it worked, on the main thread. Ignored while one is under way.
     */
    fun deletePlaylist(onDone: (Boolean) -> Unit) {
        if (_deleting.value || _saving.value) return
        _deleting.value = true
        scope.launch {
            val deleted = try {
                provider.deletePlaylist(playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't delete playlist $playlistId" }
                false
            }
            withContext(Dispatchers.Main) {
                _deleting.value = false
                _confirmingDelete.value = false
                onDone(deleted)
            }
        }
    }

    /**
     * What the server should have after the changes: [order] are the songs the page kept. Songs
     * only taken out leave [current] without them. Otherwise [current] with the songs the page
     * shows, one place each, in the new order; `null` when they are not the songs the page
     * showed.
     */
    private fun place(current: List<String>, order: List<String>): List<String>? {
        val kept = order.toSet()
        val taken = original.filter { it !in kept }.toSet()
        if (order == original.filter { it in kept })
            return current.filter { it !in taken }

        val seen = HashSet<String>()
        val slots = current.indices.filter { seen.add(current[it]) && current[it] !in hidden }
        // Only what the page showed is moved, anything else means the playlist changed
        if (slots.map { current[it] } != original) return null

        // The songs left fill the first places, the other places and the copies of the taken
        // songs go; disliked songs and copies of the ones kept stay where they are
        val rank = slots.withIndex().associate { (rank, slot) -> slot to rank }
        return buildList {
            current.forEachIndexed { index, id ->
                val place = rank[index]
                when {
                    place != null -> if (place < order.size) add(order[place])
                    id !in taken -> add(id)
                }
            }
        }
    }

    override suspend fun fetch(): OpenSubsonicPlaylistDetail? {
        val (playlist, songs, editable) = provider.playlistWithSongs(playlistId) ?: return null
        return OpenSubsonicPlaylistDetail(
            name = playlist.name,
            thumbnail = playlist.thumbnail,
            songs = songs.map { it.toLiveMediaItem() }.distinctBy { it.mediaId },
            editable = editable,
        )
    }

    override fun OpenSubsonicPlaylistDetail.withoutSongs(disliked: Set<String>) =
        copy(songs = songs.withoutDisliked(disliked))

    /**
     * A link to the playlist that anyone can open. See
     * [OpenSubsonicAlbumDetailViewModel.shareLink].
     */
    suspend fun shareLink(name: String?): Result<String> =
        provider.shareLinkOf(Shareable.Playlist(playlistId), name)
}
