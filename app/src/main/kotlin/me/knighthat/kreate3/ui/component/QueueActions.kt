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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Adds the songs right after the song playing, in the list's order. */
    PLAY_NEXT,
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
        QueueAction.PLAY_NEXT -> playNext(playable)
    }
    return when (action) {
        QueueAction.ENQUEUE ->
            if (playable.size == 1) "Added 1 song to the queue"
            else "Added ${playable.size} songs to the queue"

        QueueAction.PLAY_NEXT ->
            if (playable.size == 1) "Will play next"
            else "Will play ${playable.size} songs next"

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
 * for a radio. What the user should know is posted to [LocalNotifier].
 *
 * @param radio the setting of the list, like [Preferences.HOME_SONGS_TAP_RADIO], `null` for a
 * list that always plays from the song, like an album
 */
@Composable
fun rememberSongTap(
    radio: Preference<Boolean>?,
    songs: suspend () -> List<MediaItem>,
): (MediaItem) -> Unit {
    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val currentSongs by rememberUpdatedState(songs)

    return remember(playback, notifier, scope, radio) {
        { song ->
            scope.launch {
                val isRadio = radio?.value == true
                val list = if (isRadio) emptyList() else currentSongs()
                // The list can be a whole library, the controller can be called from any thread
                withContext(Dispatchers.Default) { playback.playTapped(song, list, isRadio) }
                    ?.let(notifier::post)
            }
        }
    }
}

/**
 * Runs a [QueueAction] with what [songs] gives at the time, and posts to [LocalNotifier] what the
 * user should know about it.
 */
@Composable
fun rememberQueueActions(songs: suspend () -> List<MediaItem>): (QueueAction) -> Unit {
    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val currentSongs by rememberUpdatedState(songs)

    return remember(playback, notifier, scope) {
        { action ->
            scope.launch {
                val songs = currentSongs()
                // The list can be a whole library, the controller can be called from any thread
                withContext(Dispatchers.Default) { playback.queue(action, songs) }
                    ?.let(notifier::post)
            }
        }
    }
}

/**
 * Like [rememberQueueActions], for a list that shows only some of the songs, like the top songs
 * of an artist. Play and Shuffle start at once with a random song of [shown], then the rest of
 * what [songs] gives follows it, in its order or a random one, unless the queue was changed
 * meanwhile. The other actions wait for [songs].
 *
 * @param songs every song, `null` when they can't be had, the song started then plays alone
 */
@Composable
fun rememberQuickQueueActions(
    shown: () -> List<MediaItem>,
    songs: suspend () -> List<MediaItem>?,
): (QueueAction) -> Unit {
    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val everySongFailed = stringResource(R.string.error_queue_every_song)
    val scope = rememberCoroutineScope()
    val currentShown by rememberUpdatedState(shown)
    val currentSongs by rememberUpdatedState(songs)

    return remember(playback, notifier, scope, everySongFailed) {
        { action ->
            scope.launch {
                val first = currentShown()
                    .filter { it.mediaMetadata.isPlayable == true }
                    .takeIf { action == QueueAction.PLAY || action == QueueAction.SHUFFLE }
                    ?.randomOrNull()
                first?.let { playback.playAll(listOf(it)) }

                val all = currentSongs()
                if (all == null) {
                    notifier.post(everySongFailed)
                    return@launch
                }
                // The list can be a whole library, the controller can be called from any thread
                withContext(Dispatchers.Default) {
                    if (first == null) return@withContext playback.queue(action, all)

                    val rest = all.filter {
                        it.mediaId != first.mediaId && it.mediaMetadata.isPlayable == true
                    }
                    playback.follow(
                        first,
                        if (action == QueueAction.SHUFFLE) rest.shuffled() else rest
                    )
                    null
                }?.let(notifier::post)
            }
        }
    }
}

/**
 * Play, then shuffle, play next and add to queue as icons, centered in a row, like under the
 * header of a detail screen.
 *
 * @param menu items of a more button at the end, see [MoreButton], `null` for no button
 */
@Composable
fun QueueActionRow(
    onAction: (QueueAction) -> Unit,
    modifier: Modifier = Modifier,
    menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
) {
    QueuePlayButton({ onAction(QueueAction.PLAY) })
    ShuffleButton({ onAction(QueueAction.SHUFFLE) })
    PlayNextButton({ onAction(QueueAction.PLAY_NEXT) })
    AddToQueueButton({ onAction(QueueAction.ENQUEUE) })
    menu?.let { MoreButton(items = it) }
}

/**
 * The main button of a row of [QueueAction]s, see [QueueAction.PLAY]: labeled, or a filled icon
 * while [LocalIconOnly].
 */
@Composable
fun QueuePlayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (LocalIconOnly.current)
    // Play stays the main action, filled like its labeled button
        FilledIconButton(onClick = onClick, modifier = modifier) {
            Icon(
                painterResource(R.drawable.play_arrow),
                contentDescription = stringResource(R.string.semantic_play)
            )
        }
    else
        LabeledButton("Play", R.drawable.play_arrow, onClick, modifier)
}

/**
 * A [MoreButton] with [QueueAction.ENQUEUE], for rows without a button for it.
 */
@Composable
fun QueueMoreButton(onAction: (QueueAction) -> Unit, modifier: Modifier = Modifier) =
    MoreButton(modifier) { close ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.semantic_add_to_queue)) },
            leadingIcon = { Icon(painterResource(R.drawable.playlist_add), null) },
            onClick = {
                close()
                onAction(QueueAction.ENQUEUE)
            },
        )
    }

@Composable
private fun LabeledButton(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) =
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
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
