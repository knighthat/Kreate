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

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.source.local.albumArtUri
import me.knighthat.kreate3.ui.component.BookmarkButton
import me.knighthat.kreate3.ui.component.DragHandle
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A album of this device's library. For an album found on the device, its more menu puts
 * handles where covers would be, to put its songs in another order, a long press there puts them
 * back in the album's order. A remote album keeps its server's order.
 */
@Composable
fun LocalAlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalAlbumDetailViewModel = koinViewModel(),
) {
    val album by viewModel.album.collectAsState()
    val canShare by viewModel.canShare.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val isReordering by viewModel.isReordering.collectAsState()
    val arranged by viewModel.arranged.collectAsState()
    val notifier = LocalNotifier.current

    LocalAlbumDetailContent(
        name = album?.name,
        cover = album?.let {
            it.thumbnail ?: it.id.toLongOrNull()
                ?.takeIf { _ -> it.origin == Origin.LOCAL }?.let(::albumArtUri)
        },
        subtitle = album?.let {
            listOfNotNull(
                it.artists?.joinToString(", "),
                it.year?.toString()
            ).takeIf { p -> p.isNotEmpty() }?.joinToString(" · ")
        },
        origin = album?.origin,
        isExplicit = album?.isExplicit == true,
        songs = viewModel.songs.collectAsLazyPagingItems(),
        isReordering = isReordering,
        arranged = arranged,
        onBack = onBack,
        onAction = rememberQueueActions(viewModel::allSongs),
        onSongClick = rememberSongTap(null, viewModel::allSongs),
        onReorder = viewModel::reorder,
        onResetOrder = {
            viewModel.resetOrder { reset ->
                notifier.post(if (reset) "Back in the album's order" else "Couldn't reset the order")
            }
        },
        onMoveSong = { song, after ->
            viewModel.moveSong(song.mediaId, after?.mediaId) { moved ->
                if (!moved) notifier.post("Couldn't move it in the album")
            }
        },
        share = if (canShare) viewModel::shareLink else null,
        isBookmarked = isBookmarked,
        onToggleBookmark = viewModel::toggleBookmark,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param arranged every song in the album's order while they are moved, see
 * [LocalAlbumDetailViewModel.arranged]
 * @param onMoveSong moves a song to right after another one, to the start after `null`
 * @param onResetOrder puts the songs back in the album's own order
 * @param share gives the album's link, `null` when it has none
 * @param isBookmarked `null` hides the bookmark button, while it isn't known
 */
@Composable
private fun LocalAlbumDetailContent(
    name: String?,
    cover: Uri?,
    subtitle: String?,
    origin: Origin?,
    isExplicit: Boolean,
    songs: LazyPagingItems<MediaItem>,
    isReordering: Boolean,
    arranged: List<MediaItem>?,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onReorder: (Boolean) -> Unit,
    onMoveSong: (song: MediaItem, after: MediaItem?) -> Unit,
    onResetOrder: () -> Unit,
    share: (suspend () -> Result<String>)?,
    isBookmarked: Boolean?,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    DetailScaffold(
        title = name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            isBookmarked?.let { BookmarkButton(it, onToggleBookmark) }
            if (share != null) ShareAction(name, "album", share)
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        // Changed at once by the finger, the album follows on the drop
        var entries by remember(arranged) { mutableStateOf(arranged) }
        // A drag handle keeps the callbacks it was first composed with, they find the song by
        // key in this instead of trusting the list they caught then
        val latestEntries by rememberUpdatedState(entries)
        var dragFrom by remember { mutableIntStateOf(-1) }
        val haptics = LocalHapticFeedback.current
        val reorder = rememberReorderableLazyListState(listState, innerPadding) { from, to ->
            // Their indices count the header's items too, the songs are found by key
            entries = entries?.toMutableList()?.apply {
                val fromIndex = indexOfFirst { it.mediaId == from.key }
                val toIndex = indexOfFirst { it.mediaId == to.key }
                if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
            }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        val lifted = MaterialTheme.colorScheme.surfaceContainerHighest
        var confirmReset by remember { mutableStateOf(false) }

        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = cover,
                    subtitle = subtitle,
                    shape = MaterialTheme.shapes.medium,
                    origin = origin,
                    isExplicit = isExplicit,
                )
            }
            item(key = "queue-actions") {
                QueueActionRow(
                    onAction = onAction,
                    // A remote album keeps the order of its server
                    menu = if (origin != Origin.LOCAL) null else { close ->
                        ReorderMenuItem(
                            isReordering = isReordering,
                            // Stays on with fewer songs, so it can be turned off
                            enabled = isReordering || songs.itemCount > 1,
                            onClick = {
                                close()
                                onReorder(!isReordering)
                            },
                            onLongClick = {
                                close()
                                confirmReset = true
                            },
                        )
                    },
                )
            }

            val moving = entries
            if (moving != null) {
                items(moving, key = { it.mediaId }) { song ->
                    ReorderableItem(reorder, key = song.mediaId) { isDragging ->
                        // Still no covers, a handle takes the place of one
                        SongItem(
                            item = song,
                            onClick = { onSongClick(song) },
                            showThumbnail = false,
                            showAlbum = false,
                            thumbnailOverlay = {
                                DragHandle(
                                    handle = Modifier.draggableHandle(
                                        onDragStarted = {
                                            dragFrom = latestEntries.orEmpty()
                                                .indexOfFirst { it.mediaId == song.mediaId }
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate
                                            )
                                        },
                                        onDragStopped = {
                                            val now = latestEntries.orEmpty()
                                            val to =
                                                now.indexOfFirst { it.mediaId == song.mediaId }
                                            if (dragFrom >= 0 && to >= 0 && to != dragFrom)
                                                onMoveSong(song, now.getOrNull(to - 1))
                                            dragFrom = -1
                                        },
                                    ),
                                )
                            },
                            // Opaque, the songs it passes over don't show through it
                            modifier = if (isDragging) Modifier.background(lifted) else Modifier,
                        )
                    }
                }
            } else {
                items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                    songs[index]?.let {
                        SongItem(
                            it,
                            onClick = { onSongClick(it) },
                            showThumbnail = false,
                            showAlbum = false,
                        )
                    }
                }
            }
        }

        if (confirmReset)
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text(stringResource(R.string.dialog_title_reset_the_order)) },
                text = { Text(stringResource(R.string.dialog_body_reset_the_order)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmReset = false
                            onResetOrder()
                        },
                    ) { Text(stringResource(R.string.action_reset)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        confirmReset = false
                    }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
    }
}

/**
 * Turns the reordering on or off, a long press calls [onLongClick]. Laid out like a
 * [DropdownMenuItem], which can't be long pressed.
 */
@Composable
private fun ReorderMenuItem(
    isReordering: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
        .let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minWidth = 112.dp, maxWidth = 280.dp, minHeight = 48.dp)
            .combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick)
            .padding(MenuDefaults.DropdownMenuItemContentPadding),
    ) {
        Icon(
            painterResource(if (isReordering) R.drawable.lock_open else R.drawable.lock),
            contentDescription = null,
            tint = color,
        )
        Text(
            if (isReordering) stringResource(R.string.semantic_lock_the_order) else stringResource(R.string.semantic_reorder),
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** How much of its color a disabled menu item keeps, like Material's. */
private const val DISABLED_ALPHA = 0.38f

@ScreenPreviews
@Composable
private fun LocalAlbumDetailPreview() = PreviewTheme {
    LocalAlbumDetailContent(
        name = "Afterglow",
        cover = null,
        subtitle = "Neon Coast · 2021",
        origin = Origin.LOCAL,
        isExplicit = true,
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        isReordering = false,
        arranged = null,
        onBack = {},
        onAction = {},
        onSongClick = {},
        onReorder = {},
        onMoveSong = { _, _ -> },
        onResetOrder = {},
        share = { Result.success("") },
        isBookmarked = true,
        onToggleBookmark = {},
    )
}
