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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import org.koin.compose.koinInject

/**
 * What a quick action does with the songs of a list.
 */
enum class QueueAction {
    /** Replaces the queue with the songs, in the list's order, and plays them. */
    PLAY,

    /** Like [PLAY], in a random order. */
    SHUFFLE,

    /** Adds the songs to the end of the queue. */
    ENQUEUE,
}

/**
 * Does [action] with the songs of [songs] that can be played.
 *
 * @return what to tell the user, `null` when the player shows the result
 */
fun PlaybackController.queue(action: QueueAction, songs: List<MediaItem>): String? {
    val playable = songs.filter { it.mediaMetadata.isPlayable == true }
    if (playable.isEmpty())
        return if (songs.isEmpty()) "Nothing to play" else "These songs can't be played"

    when (action) {
        QueueAction.PLAY -> playAll(playable)
        QueueAction.SHUFFLE -> playAll(playable.shuffled())
        QueueAction.ENQUEUE -> enqueue(playable)
    }
    return when (action) {
        QueueAction.ENQUEUE ->
            if (playable.size == 1) "Added 1 song to the queue"
            else "Added ${playable.size} songs to the queue"

        else -> null
    }
}

/**
 * Starts a new queue from [song], tapped in [songs]: with [radio] it plays [song] followed by
 * songs like it, see [PlaybackController.radio], otherwise [song] followed by the songs below it
 * in [songs] that can be played.
 *
 * @return what to tell the user, `null` when the player shows the result
 */
fun PlaybackController.playTapped(
    song: MediaItem,
    songs: List<MediaItem>,
    radio: Boolean,
): String? {
    if (song.mediaMetadata.isPlayable != true) return "This song can't be played"

    if (radio) {
        radio(song)
        return null
    }
    val below = songs.dropWhile { it.mediaId != song.mediaId }
        .drop(1)
        .filter { it.mediaMetadata.isPlayable == true }
    playAll(listOf(song) + below)
    return null
}

/**
 * What tapping a song of a list does, as [radio] says at the time: a radio when on, the list
 * from the song when off, see [playTapped]. [songs] gives the list at the time, it isn't asked
 * for a radio. What the user should know shows in [snackbar].
 *
 * @param radio the setting of the list, like [Preferences.HOME_SONGS_TAP_RADIO], `null` for a
 * list that always plays from the song, like an album
 */
@Composable
fun rememberSongTap(
    snackbar: SnackbarHostState,
    radio: Preference<Boolean>?,
    songs: suspend () -> List<MediaItem>,
): (MediaItem) -> Unit {
    val playback = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()
    val currentSongs by rememberUpdatedState(songs)

    return remember(playback, scope, snackbar, radio) {
        { song ->
            scope.launch {
                val isRadio = radio?.value == true
                val list = if (isRadio) emptyList() else currentSongs()
                val message = playback.playTapped(song, list, isRadio) ?: return@launch
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }
}

/**
 * Runs a [QueueAction] with what [songs] gives at the time, and shows in [snackbar] what the user
 * should know about it.
 */
@Composable
fun rememberQueueActions(
    snackbar: SnackbarHostState,
    songs: suspend () -> List<MediaItem>,
): (QueueAction) -> Unit {
    val playback = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()
    val currentSongs by rememberUpdatedState(songs)

    return remember(playback, scope, snackbar) {
        { action ->
            scope.launch {
                val message = playback.queue(action, currentSongs()) ?: return@launch
                // A new message replaces the one showing instead of waiting for it
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }
}

/**
 * Play and shuffle buttons with the rest of the [QueueAction]s in a menu, centered in a row, like
 * under the header of a detail screen.
 */
@Composable
fun QueueActionRow(onAction: (QueueAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        LabeledButton("Play", R.drawable.play_arrow) { onAction(QueueAction.PLAY) }
        LabeledButton("Shuffle", R.drawable.shuffle) { onAction(QueueAction.SHUFFLE) }
        QueueMoreButton(onAction)
    }
}

/**
 * A [MoreButton] with the [QueueAction]s that have no button of their own.
 */
@Composable
fun QueueMoreButton(onAction: (QueueAction) -> Unit, modifier: Modifier = Modifier) =
    MoreButton(modifier) { close ->
        DropdownMenuItem(
            text = { Text("Add to queue") },
            leadingIcon = { Icon(painterResource(R.drawable.playlist_add), null) },
            onClick = {
                close()
                onAction(QueueAction.ENQUEUE)
            },
        )
    }

@Composable
private fun LabeledButton(label: String, @DrawableRes icon: Int, onClick: () -> Unit) =
    FilledTonalButton(
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
