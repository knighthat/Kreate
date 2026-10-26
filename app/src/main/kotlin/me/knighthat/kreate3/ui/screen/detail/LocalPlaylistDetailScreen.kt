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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.CoverEditOverlay
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SongMenuAction
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.sourceRes
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A playlist of this device's library. Its more menu turns the covers into handles to move its
 * songs, or into buttons to take them out.
 */
@Composable
fun LocalPlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalPlaylistDetailViewModel = koinViewModel(),
) {
    val playlist by viewModel.playlist.collectAsState()
    val mode by viewModel.editMode.collectAsState()
    val arranged by viewModel.arranged.collectAsState()
    val notifier = LocalNotifier.current
    val moveFailed = stringResource(R.string.error_move_song)
    val removeFailed = stringResource(R.string.error_playlist_remove_song)

    val removedFrom = stringResource(
        R.string.snackbar_removed_from,
        playlist?.name ?: stringResource(R.string.snackbar_the_playlist)
    )

    LocalPlaylistDetailContent(
        name = playlist?.name,
        thumbnail = playlist?.thumbnail,
        origin = playlist?.origin,
        subtitle = playlist?.let { stringResource(it.origin.sourceRes) },
        songs = viewModel.songs.collectAsLazyPagingItems(),
        mode = mode,
        arranged = arranged,
        onBack = onBack,
        onAction = rememberQueueActions(viewModel::allSongs),
        onSongClick = rememberSongTap(null, viewModel::allSongs),
        onModeChange = viewModel::edit,
        onMoveSong = { song, after ->
            viewModel.moveSong(song.mediaId, after?.mediaId) { moved ->
                if (!moved) notifier.post(moveFailed)
            }
        },
        onRemoveSong = { song, fromMenu ->
            viewModel.removeSong(song.mediaId) { removed ->
                // A cover tapped in a row of covers to remove says nothing unless it failed
                if (fromMenu || !removed) notifier.post(
                    if (removed) removedFrom
                    else removeFailed
                )
            }
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param arranged every song in the playlist's order while they are moved, see
 * [LocalPlaylistDetailViewModel.arranged]
 * @param onMoveSong moves a song to right after another one, to the start after `null`
 * @param onRemoveSong takes a song out, `true` when it was picked in its menu
 */
@Composable
private fun LocalPlaylistDetailContent(
    name: String?,
    thumbnail: Uri?,
    origin: Origin?,
    subtitle: String?,
    songs: LazyPagingItems<MediaItem>,
    mode: CoverEditMode,
    arranged: List<MediaItem>?,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onModeChange: (CoverEditMode) -> Unit,
    onMoveSong: (song: MediaItem, after: MediaItem?) -> Unit,
    onRemoveSong: (song: MediaItem, fromMenu: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    DetailScaffold(
        title = name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
    ) { innerPadding ->
        val listState = rememberLazyListState()
        // Changed at once by the finger, the playlist follows on the drop
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

        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = thumbnail,
                    subtitle = subtitle,
                    shape = MaterialTheme.shapes.medium,
                    origin = origin,
                )
            }
            item(key = "queue-actions") {
                PlaylistActionRow(
                    onAction = onAction,
                    mode = mode,
                    onModeChange = onModeChange,
                    // Stays on while the last song is taken out, so it can be turned off
                    canEdit = mode != CoverEditMode.NONE || songs.itemCount > 0,
                )
            }

            val moving = entries
            if (moving != null) {
                items(moving, key = { it.mediaId }) { song ->
                    ReorderableItem(reorder, key = song.mediaId) { isDragging ->
                        PlaylistSong(
                            item = song,
                            mode = mode,
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
                                    val to = now.indexOfFirst { it.mediaId == song.mediaId }
                                    if (dragFrom >= 0 && to >= 0 && to != dragFrom)
                                        onMoveSong(song, now.getOrNull(to - 1))
                                    dragFrom = -1
                                },
                            ),
                            onClick = { onSongClick(song) },
                            onRemove = { onRemoveSong(song, it) },
                            // Opaque, the songs it passes over don't show through it
                            modifier = if (isDragging) Modifier.background(lifted) else Modifier,
                        )
                    }
                }
            } else {
                items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                    songs[index]?.let { song ->
                        PlaylistSong(
                            item = song,
                            // Handles come with the whole playlist, a page can't be reordered
                            mode = if (mode == CoverEditMode.REORDER) CoverEditMode.NONE else mode,
                            handle = Modifier,
                            onClick = { onSongClick(song) },
                            onRemove = { onRemoveSong(song, it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A song of the playlist, its cover as [mode] says, see [CoverEditOverlay].
 *
 * @param onRemove takes it out, `true` when picked in its menu
 */
@Composable
private fun PlaylistSong(
    item: MediaItem,
    mode: CoverEditMode,
    handle: Modifier,
    onClick: () -> Unit,
    onRemove: (fromMenu: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeLabel = stringResource(R.string.menu_remove_from_playlist)
    val remove = remember(item, onRemove, removeLabel) {
        SongMenuAction(removeLabel, R.drawable.playlist_remove) { onRemove(true) }
    }
    SongItem(
        item = item,
        onClick = onClick,
        modifier = modifier,
        thumbnailOverlay = {
            CoverEditOverlay(
                mode = mode,
                handle = handle,
                removeDescription = stringResource(R.string.semantic_remove_from_the_playlist),
                onRemove = { onRemove(false) },
            )
        },
        menuActions = listOf(remove),
    )
}

/**
 * A [QueueActionRow] whose more menu switches [mode], to move songs and to take them out, like the
 * queue's buttons: at most one is on, turning one on turns the other off.
 *
 * @param canEdit whether the switches can be used
 */
@Composable
private fun PlaylistActionRow(
    onAction: (QueueAction) -> Unit,
    mode: CoverEditMode,
    onModeChange: (CoverEditMode) -> Unit,
    canEdit: Boolean,
    modifier: Modifier = Modifier,
) {
    val reordering = mode == CoverEditMode.REORDER
    val removing = mode == CoverEditMode.REMOVE

    QueueActionRow(onAction, modifier) { close ->
        EditMenuItem(
            label = if (reordering) stringResource(R.string.semantic_lock_the_order) else stringResource(
                R.string.semantic_unlock_to_move_songs
            ),
            icon = if (reordering) R.drawable.lock_open else R.drawable.lock,
            enabled = canEdit,
        ) {
            close()
            onModeChange(if (reordering) CoverEditMode.NONE else CoverEditMode.REORDER)
        }
        EditMenuItem(
            label = if (removing) stringResource(R.string.semantic_stop_removing_songs) else stringResource(
                R.string.semantic_remove_songs
            ),
            icon = R.drawable.delete,
            enabled = canEdit,
        ) {
            close()
            onModeChange(if (removing) CoverEditMode.NONE else CoverEditMode.REMOVE)
        }
    }
}

@Composable
private fun EditMenuItem(
    label: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) = DropdownMenuItem(
    text = { Text(label) },
    leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
    enabled = enabled,
    onClick = onClick,
)

@ScreenPreviews
@Composable
private fun LocalPlaylistDetailPreview() = PreviewTheme {
    LocalPlaylistDetailContent(
        name = "Road trip",
        thumbnail = null,
        origin = Origin.LOCAL,
        subtitle = "On this device",
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        mode = CoverEditMode.NONE,
        arranged = null,
        onBack = {},
        onAction = {},
        onSongClick = {},
        onModeChange = {},
        onMoveSong = { _, _ -> },
        onRemoveSong = { _, _ -> },
    )
}
