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
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.OpenSubsonicPlaylist

private const val TAG = "OpenSubsonicPlaylist"

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

/**
 * Fetches the playlist whose id on the server is in the saved state, and its songs. Nothing is
 * stored. Its songs can be moved and taken out, see [edit].
 */
class OpenSubsonicPlaylistDetailViewModel(
    context: Context,
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
    library: UserSongRepository,
    scope: CoroutineScope,
) : LiveDetailViewModel<OpenSubsonicPlaylistDetail>(context, library.dislikedIds()) {

    val playlistId: String = checkNotNull(savedStateHandle[OpenSubsonicPlaylist.ID]) {
        "OpenSubsonicPlaylist.ID is missing from the saved state"
    }

    override val notFound: String get() = context.serverOff()

    /** Whether the user may make shares on the server, `false` until that is known. */
    val canShare = provider.canShare(viewModelScope)

    /** Moving and taking out songs, and deleting the playlist. */
    val edit = PlaylistEdit(
        scope = scope,
        shown = { (state.value as? LiveState.Ready)?.value?.songs },
        hidden = { disliked.value },
    )

    init {
        load()
    }

    /**
     * Sends the changes: songs only taken out are removed by their id, whatever else changed on
     * the server meanwhile. A new order makes the server take every song out and get them back in
     * order, which only goes ahead while the playlist is what the page showed. A song the user
     * disliked, which the page doesn't show, and a song the playlist has twice keep their
     * places. Editing ends either way, with the changes on the page when it worked, the server's
     * playlist as it was when it didn't.
     *
     * @param onDone gets whether it worked and what to tell the user, on the main thread
     */
    fun saveChanges(onDone: (saved: Boolean, message: String) -> Unit) {
        edit.save(
            commit = { changes ->
                try {
                    when (provider.rearrangePlaylist(playlistId) { place(it, changes) }) {
                        true -> SaveResult.Saved
                        false -> SaveResult.Changed
                        null -> SaveResult.Failed(context.serverOff())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ServerException) {
                    Logger.w(e, TAG) { "Couldn't save the changes of playlist $playlistId" }
                    SaveResult.Failed(e.reason(context))
                }
            },
            applied = { result, songs ->
                when (result) {
                    SaveResult.Saved -> amend { copy(songs = songs) }
                    // The server's playlist is not what the page showed, it is fetched again
                    SaveResult.Changed -> load()
                    is SaveResult.Failed -> {}
                }
            },
            onDone = { result ->
                onDone(
                    result == SaveResult.Saved,
                    when (result) {
                        SaveResult.Saved -> context.getString(R.string.snackbar_playlist_changes_saved)
                        SaveResult.Changed ->
                            context.getString(R.string.error_playlist_changed_on_server)

                        is SaveResult.Failed ->
                            context.getString(R.string.error_playlist_save_changes, result.reason)
                    }
                )
            },
        )
    }

    /**
     * Deletes the playlist from the server. [onDone] gets whether it worked, on the main thread.
     */
    fun deletePlaylist(onDone: (Boolean) -> Unit) {
        edit.delete(call = { provider.deletePlaylist(playlistId) }, onDone = onDone)
    }

    /**
     * What the server should have after the changes. Songs only taken out leave [current] without
     * them. Otherwise [current] with the songs the page shows, one place each, in the new order;
     * `null` when they are not the songs the page showed.
     */
    private fun place(current: List<String>, changes: PlaylistChanges): List<String>? {
        if (changes.onlyRemoved) return current.filter { it !in changes.removed }

        val seen = HashSet<String>()
        val slots = current.indices.filter {
            seen.add(current[it]) && current[it] !in changes.hidden
        }
        // Only what the page showed is moved, anything else means the playlist changed
        if (slots.map { current[it] } != changes.original) return null

        // The songs left fill the first places, the other places and the copies of the taken
        // songs go; disliked songs and copies of the ones kept stay where they are
        val rank = slots.withIndex().associate { (rank, slot) -> slot to rank }
        return buildList {
            current.forEachIndexed { index, id ->
                val place = rank[index]
                when {
                    place != null -> if (place < changes.order.size) add(changes.order[place])
                    id !in changes.removed -> add(id)
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
