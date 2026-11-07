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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubePlaylistEntry
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.YouTubePlaylist

/**
 * A playlist of YouTube as its screen shows it, its songs are [YouTubePlaylistDetailViewModel.songs].
 *
 * @property editable whether the signed in user may change or delete it: it is theirs
 */
data class YouTubePlaylistDetail(
    val name: String,
    val thumbnail: Uri?,
    val subtitle: String?,
    val summary: String? = null,
    val editable: Boolean = false,
)

/**
 * Fetches the playlist whose id is in the saved state from YouTube, and its songs part after part
 * as they are scrolled to. Nothing is stored. The user's own playlists can be edited, see [edit];
 * another's can be added to the library of the signed in account, see [bookmark].
 *
 * @param scope the app's, where a change is sent
 */
class YouTubePlaylistDetailViewModel(
    context: Context,
    savedStateHandle: SavedStateHandle,
    private val provider: YouTubeProvider,
    library: UserSongRepository,
    scope: CoroutineScope,
    notifier: Notifier,
) : LiveDetailViewModel<YouTubePlaylistDetail>(context, library.dislikedIds()) {

    val playlistId: String = checkNotNull(savedStateHandle[YouTubePlaylist.ID]) {
        "YouTubePlaylist.ID is missing from the saved state"
    }

    /** What the screen shows of the playlist until YouTube answers. */
    private val opened: OpenedItem = savedStateHandle.openedItem()

    override val notFound: String
        get() = context.youTubeNotFound(
            opened.name,
            R.string.error_source_not_found_playlist,
            R.string.error_source_not_found_playlist_unnamed
        )

    /**
     * Whether the playlist is in the library of the signed in account ("Add to library"). Off
     * (`null`) while signed out, for the user's own playlists and for the ones without the button.
     */
    val bookmark = SourceToggle(
        scope,
        notifier,
        send = { on -> provider.savePlaylist(playlistId, on) },
        failure = { e, _ ->
            e.youTubeFailure(
                context.resources,
                context.resources.itemName(opened.name, R.string.snackbar_this_playlist)
            )
        },
    )

    private val _songs = MutableStateFlow(emptyList<MediaItem>())
    private val _hasMore = MutableStateFlow(false)
    private var continuation: String? = null
    private var more: Job? = null

    /**
     * The songs fetched so far, in the playlist's order, each once. The ones the user disliked
     * are left out.
     */
    val songs: StateFlow<List<MediaItem>> =
        combine(_songs, disliked, List<MediaItem>::withoutDisliked)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether YouTube has songs after [songs], see [loadMore]. */
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // What names each place of the playlist, by the song's id: a song held twice has two. Only
    // the signed in owner is given them.
    private val places = MutableStateFlow(emptyMap<String, List<String>>())

    private val _preparing = MutableStateFlow(false)

    /** Whether the rest of the songs is being fetched for [edit] to begin, see [startEditing]. */
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    // Whether a request of the changes got through, the playlist is then not what the page showed
    private var touched = false

    /** Moving and taking out songs, and deleting the playlist. */
    val edit = PlaylistEdit(
        scope = scope,
        shown = { songs.value.takeIf { state.value is LiveState.Ready } },
        hidden = { disliked.value },
    )

    init {
        load()
    }

    override suspend fun fetch(): YouTubePlaylistDetail? {
        more?.cancel()
        val page = provider.playlistPage(playlistId) ?: return null
        _songs.value = emptyList()
        places.value = emptyMap()
        add(page.entries, page.continuation)
        // Asked in the session, the page tells whether the playlist is in the library
        bookmark.known(page.saved.takeIf { provider.isSignedIn() && !page.editable })
        return YouTubePlaylistDetail(
            name = page.playlist.name,
            thumbnail = provider.artwork(page.playlist.thumbnail, HEADER_ARTWORK),
            subtitle = page.author,
            summary = page.summary,
            editable = page.editable,
        )
    }

    // The songs are kept apart, in songs
    override fun YouTubePlaylistDetail.withoutSongs(disliked: Set<String>) = this

    /**
     * Fetches the next songs unless they are on their way or there are none. A failure leaves
     * the list as it is, the next call tries again.
     */
    fun loadMore() {
        val token = continuation ?: return
        if (more?.isActive == true) return
        more = viewModelScope.launch {
            try {
                val page = provider.playlistEntries(token)
                add(page.items, page.continuation)
            } catch (_: YouTubeException) {
                // Scrolling to the end again retries
            }
        }
    }

    private fun add(entries: List<YouTubePlaylistEntry>, next: String?) {
        places.update { known ->
            val grown = known.toMutableMap()
            entries.forEach { entry ->
                val id = entry.song.song.id
                entry.setVideoId?.let { grown[id] = grown[id].orEmpty() + it }
            }
            grown
        }
        val items = entries.map { entry ->
            val song = entry.song
            song.toLiveMediaItem(
                provider.artwork(song.song.thumbnail ?: song.album?.thumbnail, SONG_ARTWORK)
            )
        }
        _songs.update { shown ->
            val ids = shown.mapTo(HashSet()) { it.mediaId }
            shown + items.filter { ids.add(it.mediaId) }
        }
        continuation = next
        _hasMore.value = next != null
    }

    /**
     * Starts editing with [mode]. Moving songs needs every song of the playlist, so the rest is
     * fetched first, [preparing] tells; when that fails nothing starts and [onFailed] gets what
     * to tell the user.
     */
    fun startEditing(mode: CoverEditMode, onFailed: (String) -> Unit) {
        // Switching modes while editing has everything already
        if (edit.arranging.value != null || !_hasMore.value) {
            edit.start(mode)
            return
        }
        if (_preparing.value) return
        _preparing.value = true
        viewModelScope.launch {
            try {
                more?.join()
                while (true) {
                    val token = continuation ?: break
                    val page = provider.playlistEntries(token)
                    add(page.items, page.continuation)
                }
                edit.start(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                onFailed(e.reason(context))
            } finally {
                _preparing.value = false
            }
        }
    }

    /**
     * Sends the changes: the songs taken out in one request, then each song that has to move to
     * its place in a request of its own. Editing ends either way, with the changes on the page
     * when it worked; when it didn't, and some request got through, the playlist is fetched
     * again, else the page keeps the songs as they were.
     *
     * @param onDone gets whether it worked and what to tell the user, on the main thread
     */
    fun saveChanges(onDone: (saved: Boolean, message: String) -> Unit) {
        touched = false
        edit.save(
            commit = { changes ->
                try {
                    send(changes)
                    SaveResult.Saved
                } catch (e: CancellationException) {
                    throw e
                } catch (e: YouTubeException) {
                    Logger.w(e, TAG) { "Couldn't save the changes of playlist $playlistId" }
                    SaveResult.Failed(e.reason(context))
                }
            },
            applied = { result, arranged ->
                when (result) {
                    SaveResult.Saved -> {
                        // Songs of the playlist the user can't see keep their places in the list
                        val hidden = disliked.value
                        _songs.update { all -> arranged + all.filter { it.mediaId in hidden } }
                        val kept = arranged.mapTo(HashSet()) { it.mediaId } + hidden
                        places.update { known -> known.filterKeys { it in kept } }
                    }

                    else -> if (touched) load()
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

    private suspend fun send(changes: PlaylistChanges) {
        val known = places.value
        // A song held twice goes with every place it has
        val taken = changes.removed.flatMap { id -> known[id].orEmpty().map { it to id } }
        provider.removeFromPlaylist(playlistId, taken)
        touched = true

        // Each place by the first one of its song, which is where the page shows it
        val first = { id: String -> known[id]?.firstOrNull() }
        for ((id, before) in moves(
            changes.original.filter { it !in changes.removed },
            changes.order
        )) {
            provider.moveInPlaylist(
                playlistId,
                first(id) ?: continue,
                before?.let(first)
            )
        }
    }

    /**
     * Deletes the playlist from YouTube. [onDone] gets whether it worked, on the main thread.
     */
    fun deletePlaylist(onDone: (Boolean) -> Unit) {
        edit.delete(call = { provider.deletePlaylist(playlistId) }, onDone = onDone)
    }

    /** The playlist's public link, it needs no request. */
    fun shareLink(): Result<String> = Result.success(YouTubeLinks.playlist(playlistId))
}

private const val TAG = "YouTubePlaylist"

/**
 * The moves that turn [from] into [to], which hold the same ids: each is an id and the id it goes
 * right before, `null` for the end. Ids that are in the same order in both stay where they are,
 * the most of them that can, so a few songs moved make a few moves. The moves are to be made in
 * the order given: from the end of [to], each id goes before the one that follows it there, which
 * is already in place.
 */
internal fun moves(from: List<String>, to: List<String>): List<Pair<String, String?>> {
    if (from == to) return emptyList()
    val target = to.withIndex().associate { (index, id) -> id to index }
    val stays = longestIncreasing(from.map { target.getValue(it) }).mapTo(HashSet()) { to[it] }

    val result = ArrayList<Pair<String, String?>>()
    var following: String? = null
    for (id in to.asReversed()) {
        if (id !in stays) result += id to following
        following = id
    }
    return result
}

/** The values of a longest increasing run of [values], which are all different. */
private fun longestIncreasing(values: List<Int>): List<Int> {
    // Patience sorting: the smallest value an increasing run of each length can end with
    val tails = ArrayList<Int>()
    val tailIndex = ArrayList<Int>()
    val previous = IntArray(values.size) { -1 }
    values.forEachIndexed { index, value ->
        var low = 0
        var high = tails.size
        while (low < high) {
            val mid = (low + high) / 2
            if (tails[mid] < value) low = mid + 1 else high = mid
        }
        if (low == tails.size) {
            tails += value
            tailIndex += index
        } else {
            tails[low] = value
            tailIndex[low] = index
        }
        previous[index] = if (low > 0) tailIndex[low - 1] else -1
    }
    val run = ArrayDeque<Int>()
    var at = tailIndex.lastOrNull() ?: -1
    while (at >= 0) {
        run.addFirst(values[at])
        at = previous[at]
    }
    return run
}
