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

package me.knighthat.kreate3.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalResources
import androidx.media3.common.MediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.paging.PlaylistSort
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.SongStore
import org.koin.compose.koinInject

private const val TAG = "AddToPlaylist"

/** The playlists a song can be added to, the newest first. */
internal val NEWEST_FIRST = SortOrder(PlaylistSort.CREATED_AT, SortDirection.DESCENDING)

/**
 * The playlists that a song can go in, the ones made in the app and the ones of its own source, and
 * how to put it there. Each write runs
 * on the app's scope, so it lands even once the menu is gone, and says how it went with a message.
 */
@Immutable
internal class AddToPlaylist(
    /** The playlists made in the app, the newest first, `null` while they load. */
    val playlists: List<UserPlaylist>?,
    val add: (UserPlaylist) -> Unit,
    /** The playlists of the song's own source, fetched when the menu opens. */
    val remote: RemotePlaylists,
    val addRemote: (UserPlaylist) -> Unit,
)

/** The playlists of the source a song comes from, fetched live and never stored. */
@Immutable
internal sealed interface RemotePlaylists {

    /** There is none to show: a song of the device, or its source is off or signed out. */
    data object None : RemotePlaylists

    data object Loading : RemotePlaylists

    /** The source couldn't be asked. */
    data object Failed : RemotePlaylists

    /** The ones the user may add the song to, empty when there are none. */
    data class Loaded(val playlists: List<UserPlaylist>) : RemotePlaylists
}

/**
 * The playlists [load] gives, as the menu shows them while and after they are fetched. Called from
 * composition, so [load] runs off the main thread: its answer is mapped to playlists where it
 * runs.
 */
private suspend fun fetchRemote(load: suspend () -> List<UserPlaylist>?): RemotePlaylists =
    try {
        withContext(Dispatchers.Default) { load() }
            ?.let { RemotePlaylists.Loaded(it) } ?: RemotePlaylists.None
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, TAG) { "Couldn't fetch the playlists of the song's source" }
        RemotePlaylists.Failed
    }

/**
 * Puts the song of [item] in a playlist made in the app. A song fetched live is stored first, see
 * [SongStore].
 */
@Composable
internal fun rememberAddToPlaylist(item: MediaItem): AddToPlaylist {
    val repository = koinInject<PlaylistRepository>()
    val songs = koinInject<SongStore>()
    val youTube = koinInject<YouTubeProvider>()
    val openSubsonic = koinInject<OpenSubsonicProvider>()
    val scope = koinInject<CoroutineScope>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current

    val playlists by produceState<List<UserPlaylist>?>(null, repository) {
        value = repository.list(NEWEST_FIRST, setOf(Origin.LOCAL))
    }

    val origin = item.origin
    val remote by produceState<RemotePlaylists>(
        if (origin == Origin.YOUTUBE || origin == Origin.OPEN_SUBSONIC)
            RemotePlaylists.Loading
        else
            RemotePlaylists.None,
        item.mediaId,
    ) {
        value = when (origin) {
            Origin.YOUTUBE -> fetchRemote { youTube.addablePlaylists(item.mediaId) }
            Origin.OPEN_SUBSONIC -> fetchRemote { openSubsonic.addablePlaylists() }
            else -> RemotePlaylists.None
        }
    }

    /** Runs [undo] on the app's scope, the menu is long gone by the time it's tapped. */
    fun undoing(playlist: String, undo: suspend () -> Unit): () -> Unit = {
        scope.launch {
            try {
                undo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't take ${item.mediaId} back out of $playlist" }
            }
        }
    }

    /** @param add puts the song in, giving how to take it back out, `null` when it was in already */
    fun write(playlist: String, add: suspend () -> (suspend () -> Unit)?) = scope.launch {
        val message = try {
            // The song has to be in the database before a playlist can have it
            songs.store(item.mediaId, item.origin)
            val undo = add()
            if (undo == null) {
                Message(
                    resources.getString(
                        R.string.snackbar_already_in_playlist,
                        playlist
                    )
                )
            } else {
                undoMessage(
                    resources,
                    resources.getString(R.string.snackbar_added_to_playlist, playlist),
                    undoing(playlist, undo),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't add ${item.mediaId} to $playlist" }
            Message(resources.getString(R.string.snackbar_couldnt_add_to_playlist, playlist))
        }
        notifier.post(message)
    }

    return AddToPlaylist(
        remote = remote,
        addRemote = { playlist ->
            val name = playlist.name
            val id = playlist.remoteId
            scope.launch {
                val message = try {
                    val added = when {
                        id == null -> null
                        origin == Origin.YOUTUBE -> youTube.addToPlaylist(id, item.mediaId)
                        origin == Origin.OPEN_SUBSONIC -> openSubsonic.addToPlaylist(
                            id,
                            item.mediaId
                        )

                        else -> null
                    }
                    when (added) {
                        true -> Message(
                            resources.getString(
                                R.string.snackbar_added_to_playlist,
                                name
                            )
                        )

                        false -> Message(
                            resources.getString(
                                R.string.snackbar_already_in_playlist,
                                name
                            )
                        )

                        null -> Message(
                            resources.getString(
                                R.string.snackbar_couldnt_add_to_playlist,
                                name
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e, TAG) { "Couldn't add ${item.mediaId} to $name" }
                    Message(resources.getString(R.string.snackbar_couldnt_add_to_playlist, name))
                }
                notifier.post(message)
            }
        },
        playlists = playlists,
        add = { playlist ->
            write(playlist.name) {
                if (!repository.addSong(playlist.id, item.mediaId))
                    null
                else
                    suspend { repository.removeSong(playlist.id, item.mediaId) }
            }
        },
    )
}
