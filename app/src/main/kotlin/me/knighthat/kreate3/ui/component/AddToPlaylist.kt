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

import android.util.Log
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.paging.PlaylistSort
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.source.SongStore
import org.koin.compose.koinInject

private const val TAG = "AddToPlaylist"

/** The playlists a song can be added to, the newest first. */
private val NEWEST_FIRST = SortOrder(PlaylistSort.CREATED_AT, SortDirection.DESCENDING)

/**
 * The playlists made in the app that a song can go in, and how to put it there. Each write runs
 * on the app's scope, so it lands even once the menu is gone, and says how it went with a message.
 */
@Immutable
internal class AddToPlaylist(
    /** The playlists made in the app, the newest first, `null` while they load. */
    val playlists: List<UserPlaylist>?,
    val add: (UserPlaylist) -> Unit,
    /** Makes a playlist called `name` and puts the song in it. */
    val create: (name: String) -> Unit,
)

/**
 * Puts the song of [item] in a playlist made in the app. A song fetched live is stored first, see
 * [SongStore].
 */
@Composable
internal fun rememberAddToPlaylist(item: MediaItem): AddToPlaylist {
    val repository = koinInject<PlaylistRepository>()
    val songs = koinInject<SongStore>()
    val scope = koinInject<CoroutineScope>()
    val notifier = LocalNotifier.current

    val playlists by produceState<List<UserPlaylist>?>(null, repository) {
        value = repository.list(NEWEST_FIRST, setOf(Origin.LOCAL))
    }

    fun write(playlist: String, add: suspend () -> Boolean) = scope.launch {
        val message = try {
            // The song has to be in the database before a playlist can have it
            songs.store(item.mediaId, item.origin)
            if (add()) "Added to $playlist" else "Already in $playlist"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't add ${item.mediaId} to $playlist", e)
            "Couldn't add to $playlist"
        }
        notifier.post(message)
    }

    return AddToPlaylist(
        playlists = playlists,
        add = { playlist ->
            write(playlist.name) {
                repository.addSong(
                    playlist.id,
                    item.mediaId
                )
            }
        },
        create = { name ->
            val trimmed = name.trim()
            // Made once the song is stored, so a song that can't be leaves no empty playlist
            write(trimmed) {
                val id = repository.save(UserPlaylist(origin = Origin.LOCAL, name = trimmed))
                repository.addSong(id, item.mediaId)
            }
        },
    )
}

/**
 * Asks for the name of a new playlist, a name is needed.
 */
@Composable
fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(
                    stringResource(R.string.action_create)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
