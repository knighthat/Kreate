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

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.songTitle
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import org.koin.compose.koinInject

/**
 * Does [action] with the songs of [songs] that can be played.
 *
 * @return what to tell the user, with an Undo button for songs added to the queue, `null` when
 * the player shows the result
 */
fun PlaybackController.queue(
    resources: Resources,
    action: QueueAction,
    songs: List<MediaItem>
): Message? {
    val playable = songs.filter { it.mediaMetadata.isPlayable == true }
    if (playable.isEmpty())
        return Message(
            resources.getString(
                if (songs.isEmpty()) R.string.error_nothing_to_play else R.string.error_songs_not_playable
            )
        )

    // Where they go, for the Undo to find them again
    val start = when (action) {
        QueueAction.ENQUEUE -> queue.value.size
        else -> state.value.index + 1
    }
    when (action) {
        QueueAction.PLAY -> play(playable)
        QueueAction.SHUFFLE -> play(playable.shuffled())
        QueueAction.ENQUEUE, QueueAction.PLAY_NEXT -> add(playable, start)
    }
    val text = when (action) {
        QueueAction.ENQUEUE ->
            resources.getString(R.string.snackbar_added_to_queue, songsLabel(resources, playable))

        QueueAction.PLAY_NEXT -> willPlayNext(resources, playable)

        else -> return null
    }
    return undoMessage(resources, text) { undoAdd(playable, start) }
}

/**
 * Takes back [items] that [PlaybackController.add] put at [start]. Only while they still sit there:
 * once the queue changed around them, nothing is taken out rather than songs that weren't added.
 */
internal fun PlaybackController.undoAdd(items: List<MediaItem>, start: Int) {
    val queued = queue.value
    val end = start + items.size
    if (start < 0 || end > queued.size) return
    if (items.indices.any { queued[start + it].mediaId != items[it].mediaId }) return
    remove(start, end)
}

/**
 * [PlaybackController.stop], posting to [notifier] a message whose Undo puts the queue back as it
 * was: on the same song and spot, playing when it was. Only on the main thread, it reads where
 * the song is.
 */
internal fun PlaybackController.stopUndoable(resources: Resources, notifier: Notifier) {
    val items = queue.value
    val state = state.value
    val position = position()
    stop()
    if (items.isEmpty()) return
    notifier.post(undoMessage(resources, resources.getString(R.string.snackbar_playback_stopped)) {
        restore(items, state.index, position, state.isPlaying)
    })
}

/**
 * [PlaybackController.clear], posting to [notifier] a message whose Undo puts the songs taken
 * out back around the one playing.
 */
internal fun PlaybackController.clearUndoable(resources: Resources, notifier: Notifier) {
    val items = queue.value
    val index = state.value.index
    clear()
    // Nothing but the current song, nothing was taken out
    if (index !in items.indices || items.size == 1) return
    val before = items.subList(0, index).toList()
    val after = items.subList(index + 1, items.size).toList()
    notifier.post(undoMessage(resources, resources.getString(R.string.snackbar_queue_cleared)) {
        // In order, the second one counts the first one's songs
        add(before, 0)
        add(after, before.size + 1)
    })
}

/** The song's quoted title when [songs] is one, else how many there are, like "3 songs". */
internal fun songsLabel(resources: Resources, songs: List<MediaItem>): String {
    val title = songs.singleOrNull()?.songTitle()
    return if (title != null)
        resources.getString(R.string.quoted, title)
    else
        resources.getQuantityString(R.plurals.song, songs.size, songs.size)
}

/** "Will play next", saying which song, or how many songs when there are several. */
internal fun willPlayNext(resources: Resources, songs: List<MediaItem>): String =
    resources.getString(R.string.snackbar_will_play_next, songsLabel(resources, songs))

/**
 * Starts a new queue from [song], tapped in [songs]: with [radio] it plays [song] followed by
 * songs like it, see [PlaybackController.radio], otherwise [song] followed by the songs below it
 * in [songs] that can be played.
 *
 * @return what to tell the user, `null` when the player shows the result
 */
fun PlaybackController.playTapped(
    resources: Resources,
    song: MediaItem,
    songs: List<MediaItem>,
    radio: Boolean,
): String? {
    if (song.mediaMetadata.isPlayable != true) return resources.getString(R.string.error_song_not_playable)

    if (radio) {
        radio(song)
        return null
    }
    val below = songs.dropWhile { it.mediaId != song.mediaId }
        .drop(1)
        .filter { it.mediaMetadata.isPlayable == true }
    play(listOf(song) + below)
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

    val resources = LocalResources.current

    return remember(playback, notifier, scope, radio, resources) {
        { song ->
            scope.launch {
                val isRadio = radio?.value == true
                val list = if (isRadio) emptyList() else currentSongs()
                // The list can be a whole library, the controller can be called from any thread
                withContext(Dispatchers.Default) {
                    playback.playTapped(
                        resources,
                        song,
                        list,
                        isRadio
                    )
                }
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

    val resources = LocalResources.current

    return remember(playback, notifier, scope, resources) {
        { action ->
            scope.launch {
                val songs = currentSongs()
                // The list can be a whole library, the controller can be called from any thread
                withContext(Dispatchers.Default) { playback.queue(resources, action, songs) }
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

    val resources = LocalResources.current

    return remember(playback, notifier, scope, everySongFailed, resources) {
        { action ->
            scope.launch {
                val first = currentShown()
                    .filter { it.mediaMetadata.isPlayable == true }
                    .takeIf { action == QueueAction.PLAY || action == QueueAction.SHUFFLE }
                    ?.randomOrNull()
                first?.let { playback.play(listOf(it)) }

                val all = currentSongs()
                if (all == null) {
                    notifier.post(everySongFailed)
                    return@launch
                }
                // The list can be a whole library, the controller can be called from any thread
                withContext(Dispatchers.Default) {
                    if (first == null) return@withContext playback.queue(resources, action, all)

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
    if (LocalIconOnly.current) {
    // Play stays the main action, filled like its labeled button
        Tooltip(stringResource(R.string.semantic_play)) {
            FilledIconButton(onClick = onClick, modifier = modifier) {
                Icon(
                    painterResource(R.drawable.play_arrow),
                    contentDescription = stringResource(R.string.semantic_play)
                )
            }
        }
    } else {
        LabeledButton("Play", R.drawable.play_arrow, onClick, modifier)
    }
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

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun QueueActionRowPreview() = PreviewTheme {
    Surface {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .width(400.dp)
                .padding(vertical = 8.dp),
        ) {
            QueueActionRow(onAction = {})
            QueueActionRow(onAction = {}, menu = { close ->
                DropdownMenuItem(text = { Text("Item") }, onClick = close)
            })
            CompositionLocalProvider(LocalIconOnly provides true) {
                QueueActionRow(onAction = {})
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                QueuePlayButton({})
                QueueMoreButton({})
            }
        }
    }
}
//</editor-fold>
