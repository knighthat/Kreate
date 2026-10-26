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

package me.knighthat.kreate3.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.CoverEditOverlay
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.SongItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** How strongly the song playing stands out from the rest of the queue. */
private const val CURRENT_HIGHLIGHT_ALPHA = 0.16f

/**
 * A song of the queue, [key] tells apart the same song queued more than once.
 *
 * @param index where it is in the queue the controller sent
 */
private data class QueueEntry(val key: String, val item: MediaItem, val index: Int)

/** The queue's songs, the same song counted on each time it comes again. */
private fun List<MediaItem>.toEntries(): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    return mapIndexed { index, item ->
        val count = (seen[item.mediaId] ?: 0) + 1
        seen[item.mediaId] = count
        QueueEntry("${item.mediaId}#$count", item, index)
    }
}

/**
 * Every song of the queue, the one playing highlighted, in the order they play: shuffled while
 * shuffle is on, see [PlaybackController.shuffledOrder]. It opens with that song near the top,
 * the one before it above. Tapping a song plays it. In [mode] the covers turn into handles to
 * drag songs to another place, or into buttons to remove them.
 *
 * @param currentIndex where the song playing is in the queue, `-1` when none is
 */
@Composable
internal fun QueuePage(
    controller: PlaybackController,
    currentIndex: Int,
    mode: CoverEditMode,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val queue by controller.queue.collectAsStateWithLifecycle()
    val shuffledOrder by controller.shuffledOrder.collectAsStateWithLifecycle()
    val queued = remember(queue) { queue.toEntries() }
    // An order that doesn't count the queue's songs is the one before the queue changed, the
    // queue's own shows until the new one comes
    val shown = remember(queued, shuffledOrder) {
        shuffledOrder?.takeIf { it.size == queued.size }?.map(queued::get) ?: queued
    }
    val isShuffledOrder = shown !== queued
    // Changed at once by the finger, the controller follows and the next queue it sends agrees
    var entries by remember(shown) { mutableStateOf(shown) }
    // By key, it stays on the song playing while the songs move around it
    val currentKey = queued.getOrNull(currentIndex)?.key
    // A drag handle keeps the callbacks it was first composed with, they find the song by key
    // in these instead of trusting the index and the list they caught then
    val latestEntries by rememberUpdatedState(entries)

    /**
     * Where [entry] is in the controller's queue, which runs the calls in order. In the queue's
     * order, that is its row once the songs moved or removed here are. In the shuffled one, the
     * songs removed here before it in the queue have moved it up.
     */
    fun positionOf(entry: QueueEntry): Int {
        if (!isShuffledOrder) return latestEntries.indexOfFirst { it.key == entry.key }
        val kept = latestEntries.mapTo(HashSet()) { it.key }
        return entry.index - queued.count { it.index < entry.index && it.key !in kept }
    }

    var dragFrom by remember { mutableIntStateOf(-1) }
    val haptics = LocalHapticFeedback.current
    val reorder = rememberReorderableLazyListState(listState, contentPadding) { from, to ->
        entries = entries.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = CURRENT_HIGHLIGHT_ALPHA)
    val lifted = MaterialTheme.colorScheme.surfaceContainerHighest

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.key }) { entry ->
            ReorderableItem(reorder, key = entry.key) { isDragging ->
                val background = Modifier
                    // Opaque, the songs it passes over don't show through it
                    .then(if (isDragging) Modifier.background(lifted) else Modifier)
                    .then(if (entry.key == currentKey) Modifier.background(highlight) else Modifier)

                SongItem(
                    item = entry.item,
                    onClick = { controller.skipTo(positionOf(entry)) },
                    modifier = background,
                    hasMenu = false,
                    thumbnailOverlay = {
                        CoverEditOverlay(
                            mode = mode,
                            handle = Modifier.draggableHandle(
                                onDragStarted = {
                                    dragFrom = latestEntries.indexOfFirst { it.key == entry.key }
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = {
                                    val to = latestEntries.indexOfFirst { it.key == entry.key }
                                    if (dragFrom >= 0 && to >= 0 && to != dragFrom)
                                        controller.move(dragFrom, to)
                                    dragFrom = -1
                                },
                            ),
                            removeDescription = stringResource(R.string.semantic_remove_from_the_queue),
                            onRemove = {
                                // Before it leaves the list, which the next tap counts it out of
                                val position = positionOf(entry)
                                entries = entries.filterNot { it.key == entry.key }
                                controller.remove(position)
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * The buttons of [QueuePage]: back to the song playing, the switches of [mode], to move songs
 * and to remove them, and one to clear the queue once confirmed. At most one mode is on, turning
 * one on turns the other off.
 *
 * While [isShuffled], songs can't be moved: the page shows the shuffled order they play in,
 * moving would change the queue's own. The button looks off and tapping it says to turn shuffle
 * off first.
 */
@Composable
internal fun QueueButtons(
    controller: PlaybackController,
    mode: CoverEditMode,
    isShuffled: Boolean,
    onModeChange: (CoverEditMode) -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
) {
    val notifier = LocalNotifier.current
    val turnShuffleOff = stringResource(R.string.snackbar_turn_shuffle_off)
    val reordering = mode == CoverEditMode.REORDER
    val removing = mode == CoverEditMode.REMOVE
    var confirmClear by remember { mutableStateOf(false) }

    ModeButton(
        R.drawable.my_location,
        stringResource(R.string.semantic_go_to_the_song_playing),
        isOn = false,
        onClick = onLocate
    )
    ModeButton(
        icon = if (reordering) R.drawable.lock_open else R.drawable.lock,
        description = when {
            isShuffled -> stringResource(R.string.semantic_turn_shuffle_off_to_move_songs)
            reordering -> stringResource(R.string.semantic_lock_the_order)
            else -> stringResource(R.string.semantic_unlock_to_move_songs)
        },
        isOn = reordering,
        dimmed = isShuffled,
        onClick = {
            if (isShuffled) notifier.post(turnShuffleOff)
            else onModeChange(if (reordering) CoverEditMode.NONE else CoverEditMode.REORDER)
        },
    )
    ModeButton(
        icon = R.drawable.delete,
        description = if (removing) stringResource(R.string.semantic_stop_removing_songs) else stringResource(
            R.string.semantic_remove_songs
        ),
        isOn = removing,
        onClick = { onModeChange(if (removing) CoverEditMode.NONE else CoverEditMode.REMOVE) },
    )
    ModeButton(
        icon = R.drawable.clear_all,
        description = stringResource(R.string.semantic_clear_the_queue),
        isOn = false,
        onClick = { confirmClear = true },
    )

    if (confirmClear)
        ClearQueueDialog(
            onDismiss = { confirmClear = false },
            onConfirm = { removeCurrent ->
                confirmClear = false
                onModeChange(CoverEditMode.NONE)
                // Nothing is left to play then, the player closes by itself
                if (removeCurrent) controller.stop() else controller.clear()
            },
        )
}

/**
 * Asks before the queue is cleared. The song playing stays unless the box to remove it too is
 * checked, which says the player stops and closes then.
 *
 * @param onConfirm told whether the song playing goes too
 */
@Composable
private fun ClearQueueDialog(onDismiss: () -> Unit, onConfirm: (removeCurrent: Boolean) -> Unit) {
    var removeCurrent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_clear_the_queue)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_body_clear_the_queue_1))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = removeCurrent,
                            role = Role.Checkbox,
                            onValueChange = { removeCurrent = it },
                        ),
                ) {
                    // The row takes the tap, the box only shows it
                    Checkbox(checked = removeCurrent, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.dialog_body_clear_the_queue_2),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (removeCurrent)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.dialog_body_clear_the_queue_3),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(removeCurrent) }) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
