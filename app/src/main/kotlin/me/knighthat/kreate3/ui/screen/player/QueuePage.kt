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

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.component.SongItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** How strongly the song playing stands out from the rest of the queue. */
private const val CURRENT_HIGHLIGHT_ALPHA = 0.16f

/** How much a cover darkens under the handle or the remove button over it. */
private const val COVER_SCRIM_ALPHA = 0.55f

/** What the covers of the queue do, picked with [QueueButtons]. */
internal enum class QueueEditMode {
    /** Nothing, they are only covers. */
    NONE,

    /** Each one is a handle to drag its song to another place in the queue. */
    REORDER,

    /** Each one is a button that takes its song out of the queue. */
    REMOVE,
}

/** A song of the queue, [key] tells apart the same song queued more than once. */
private data class QueueEntry(val key: String, val item: MediaItem)

/** The queue's songs, the same song counted on each time it comes again. */
private fun List<MediaItem>.toEntries(): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    return map { item ->
        val count = (seen[item.mediaId] ?: 0) + 1
        seen[item.mediaId] = count
        QueueEntry("${item.mediaId}#$count", item)
    }
}

/**
 * Every song of the queue, the one playing highlighted. It opens with that song near the top,
 * the one before it above. Tapping a song plays it. In [mode] the covers turn into handles to
 * drag songs to another place, or into buttons to remove them.
 *
 * @param currentIndex where the song playing is in the queue, `-1` when none is
 */
@Composable
internal fun QueuePage(
    controller: PlaybackController,
    currentIndex: Int,
    mode: QueueEditMode,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val queue by controller.queue.collectAsStateWithLifecycle()
    val queued = remember(queue) { queue.toEntries() }
    // Changed at once by the finger, the controller follows and the next queue it sends agrees
    var entries by remember(queued) { mutableStateOf(queued) }
    // By key, it stays on the song playing while the songs move around it
    val currentKey = queued.getOrNull(currentIndex)?.key
    // A drag handle keeps the callbacks it was first composed with, they find the song by key
    // in these instead of trusting the index and the list they caught then
    val latestEntries by rememberUpdatedState(entries)
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
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            ReorderableItem(reorder, key = entry.key) { isDragging ->
                val background = Modifier
                    // Opaque, the songs it passes over don't show through it
                    .then(if (isDragging) Modifier.background(lifted) else Modifier)
                    .then(if (entry.key == currentKey) Modifier.background(highlight) else Modifier)

                SongItem(
                    item = entry.item,
                    onClick = { controller.skipTo(index) },
                    modifier = background,
                    hasMenu = false,
                    thumbnailOverlay = {
                        Crossfade(mode, Modifier.matchParentSize(), label = "cover") { shown ->
                            when (shown) {
                                QueueEditMode.NONE -> {}
                                QueueEditMode.REORDER -> CoverIcon(
                                    icon = R.drawable.drag_handle,
                                    description = "Drag to move",
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            dragFrom = latestEntries.indexOfFirst {
                                                it.key == entry.key
                                            }
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate
                                            )
                                        },
                                        onDragStopped = {
                                            val to = latestEntries.indexOfFirst {
                                                it.key == entry.key
                                            }
                                            if (dragFrom >= 0 && to >= 0 && to != dragFrom)
                                                controller.move(dragFrom, to)
                                            dragFrom = -1
                                        },
                                    ),
                                )

                                QueueEditMode.REMOVE -> CoverIcon(
                                    icon = R.drawable.close,
                                    description = "Remove from the queue",
                                    modifier = Modifier.clickable {
                                        // The controller's calls run in order, the next tap's
                                        // index already counts this one out
                                        entries = entries.filterNot { it.key == entry.key }
                                        controller.remove(index)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** [icon] in white over the cover darkened, in the cover's shape. */
@Composable
private fun CoverIcon(
    @DrawableRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .fillMaxSize()
        .clip(MaterialTheme.shapes.small)
        .background(Color.Black.copy(alpha = COVER_SCRIM_ALPHA))
        .then(modifier),
) {
    Icon(painterResource(icon), contentDescription = description, tint = Color.White)
}

/**
 * The buttons of [QueuePage]: back to the song playing, and the switches of [mode], to move
 * songs and to remove them. At most one mode is on, turning one on turns the other off.
 */
@Composable
internal fun QueueButtons(
    mode: QueueEditMode,
    onModeChange: (QueueEditMode) -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
) {
    val reordering = mode == QueueEditMode.REORDER
    val removing = mode == QueueEditMode.REMOVE

    ModeButton(R.drawable.my_location, "Go to the song playing", isOn = false, onClick = onLocate)
    ModeButton(
        icon = if (reordering) R.drawable.lock_open else R.drawable.lock,
        description = if (reordering) "Lock the order" else "Unlock to move songs",
        isOn = reordering,
        onClick = { onModeChange(if (reordering) QueueEditMode.NONE else QueueEditMode.REORDER) },
    )
    ModeButton(
        icon = R.drawable.delete,
        description = if (removing) "Stop removing songs" else "Remove songs",
        isOn = removing,
        onClick = { onModeChange(if (removing) QueueEditMode.NONE else QueueEditMode.REMOVE) },
    )
}
