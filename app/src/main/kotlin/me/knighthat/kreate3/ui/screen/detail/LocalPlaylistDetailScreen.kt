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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.CoverEditOverlay
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SongMenuAction
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.songsLabel
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.theme.CoverTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A playlist of this device's library. Its ⋮ menu turns the covers into handles to move its
 * songs, or into buttons to take them out.
 */
@Composable
fun LocalPlaylistDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalPlaylistDetailViewModel = koinViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val mode by viewModel.editMode.collectAsStateWithLifecycle()
    val arranged by viewModel.arranged.collectAsStateWithLifecycle()
    val reversed by viewModel.reversed.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val moveFailed = stringResource(R.string.error_move_song)
    val removeFailed = stringResource(R.string.error_playlist_remove_song)
    val deleteFailed = stringResource(R.string.error_playlist_delete)

    val songs = viewModel.songs.collectAsLazyPagingItems()
    val providers = koinInject<Providers>()
    // Counted again whenever the songs change, its pages may not hold them all
    var overview by remember { mutableStateOf<PlaylistOverview?>(null) }
    LaunchedEffect(songs.loadState.refresh) {
        if (songs.loadState.refresh is LoadState.NotLoading)
            overview = viewModel.overview(providers)
    }
    val cover = playlist?.thumbnail
    val loaded = songs.itemSnapshotList.items
    // The header stays the same whichever way the songs run: the pages start at the wrong end
    val songCovers = remember(loaded, providers, reversed, overview) {
        if (reversed) overview?.covers.orEmpty() else loaded.songCovers(providers)
    }
    val coverModels = remember(cover, songCovers) {
        listOfNotNull(thumbnailModel(cover, null)).ifEmpty { songCovers.take(1) }
    }

    CoverTheme(coverModels) {
        LocalPlaylistDetailContent(
            name = playlist?.name,
            thumbnail = playlist?.thumbnail,
            songCovers = songCovers,
            summary = overview?.takeIf { it.count > 0 }
                ?.let {
                    listOfNotNull(resources.songCount(it.count), resources.totalLength(it.length))
                }.orEmpty(),
            origin = playlist?.origin,
            songs = songs,
            mode = mode,
            arranged = arranged,
            reversed = reversed,
            onToggleOrder = { Preferences.PLAYLIST_REVERSED.update { !it } },
            onBack = onBack,
            onEdit = onEdit,
            canRemove = playlist?.isMonthly == false,
            onDelete = {
                val name = playlist?.name
                viewModel.delete { deleted ->
                    if (deleted) {
                        notifier.post(
                            resources.getString(
                                R.string.snackbar_playlist_deleted,
                                name?.let { resources.getString(R.string.quoted, it) }
                                    ?: resources.getString(R.string.snackbar_this_playlist)
                            )
                        )
                        onBack()
                    } else {
                        notifier.post(deleteFailed)
                    }
                }
            },
            onAction = rememberQueueActions(viewModel::allSongs),
            onSongClick = rememberSongTap(null, viewModel::allSongs),
            onModeChange = viewModel::edit,
            onMoveSong = { song, after ->
                viewModel.moveSong(song.mediaId, after?.mediaId) { moved ->
                    if (!moved) notifier.post(moveFailed)
                }
            },
            onRemoveSong = { song ->
                viewModel.removeSong(song.mediaId) { removed, undo ->
                    // The song is gone from the page, its name says what was removed
                    val removedFrom = resources.getString(
                        R.string.snackbar_removed_from_playlist,
                        songsLabel(resources, listOf(song))
                    )
                    notifier.post(
                        when {
                            !removed -> Message(removeFailed)
                            undo != null -> undoMessage(resources, removedFrom, undo)
                            else -> Message(removedFrom)
                        }
                    )
                }
            },
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param arranged every song in the playlist's order while they are moved, see
 * [LocalPlaylistDetailViewModel.arranged]
 * @param reversed whether [songs] run from the playlist's end
 * @param onToggleOrder turns them around
 * @param onMoveSong moves a song to right after another one, to the start after `null`
 * @param onRemoveSong takes a song out
 * @param onEdit opens the screen to change the playlist's name, picture and pin
 * @param canRemove whether songs can be taken out, not of a monthly playlist
 * @param onDelete deletes the playlist, once the user confirmed
 */
@Composable
private fun LocalPlaylistDetailContent(
    name: String?,
    thumbnail: Uri?,
    songCovers: List<Any>,
    summary: List<String>,
    origin: Origin?,
    songs: LazyPagingItems<MediaItem>,
    mode: CoverEditMode,
    arranged: List<MediaItem>?,
    reversed: Boolean,
    onToggleOrder: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    canRemove: Boolean,
    onDelete: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onModeChange: (CoverEditMode) -> Unit,
    onMoveSong: (song: MediaItem, after: MediaItem?) -> Unit,
    onRemoveSong: (song: MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val listState = rememberLazyListState()
    // Changed at once by the finger, the playlist follows on the drop
    var entries by remember(arranged) { mutableStateOf(arranged) }
    // A drag handle keeps the callbacks it was first composed with, they find the song by
    // key in this instead of trusting the list they caught then
    val latestEntries by rememberUpdatedState(entries)
    var dragFrom by remember { mutableIntStateOf(-1) }
    val haptics = LocalHapticFeedback.current
    val reorder = rememberReorderableLazyListState(listState, contentPadding) { from, to ->
        // Their indices count the header too, the songs are found by key
        entries = entries?.toMutableList()?.apply {
            val fromIndex = indexOfFirst { it.mediaId == from.key }
            val toIndex = indexOfFirst { it.mediaId == to.key }
            if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val lifted = MaterialTheme.colorScheme.surfaceContainerHighest
    val models = remember(thumbnail) { listOfNotNull(thumbnailModel(thumbnail, null)) }
    val count = songs.itemCount
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    PlaylistDetailScaffold(
        name = name.orEmpty(),
        models = models,
        songCovers = songCovers,
        owner = null,
        summary = summary,
        origin = origin,
        onBack = onBack,
        onAction = onAction.takeIf { count > 0 },
        share = null,
        canShare = false,
        modifier = modifier,
        contentPadding = contentPadding,
        listState = listState,
        reversed = reversed,
        onToggleOrder = onToggleOrder.takeIf { count > 0 && mode == CoverEditMode.NONE },
        menu = { onDismiss ->
            PlaylistMenu(
                name = name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { count > 0 },
                share = null,
                canShare = false,
                mode = mode,
                // Stays on while the last song is taken out, so it can be turned off
                canEdit = mode != CoverEditMode.NONE || count > 0,
                canRemove = canRemove,
                onEdit = onEdit,
                onDelete = { confirmDelete = true },
                onModeChange = onModeChange,
            )
        },
    ) {
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
                        onRemove = { onRemoveSong(song) },
                        canRemove = canRemove,
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
                        onRemove = { onRemoveSong(song) },
                        canRemove = canRemove,
                    )
                }
            }
        }
    }

    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_title_delete_playlist)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_body_delete_playlist,
                        stringResource(R.string.quoted, name.orEmpty())
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
}

/**
 * A song of the playlist, its cover as [mode] says, see [CoverEditOverlay].
 *
 * @param onRemove takes it out, from its menu or its cover
 * @param canRemove whether it can be taken out, else neither of them offers it
 */
@Composable
private fun PlaylistSong(
    item: MediaItem,
    mode: CoverEditMode,
    handle: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    modifier: Modifier = Modifier,
) {
    val removeLabel = stringResource(R.string.menu_remove_from_playlist)
    val remove = remember(item, onRemove, removeLabel) {
        SongMenuAction(removeLabel, R.drawable.playlist_remove, onRemove)
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
                onRemove = onRemove,
            )
        },
        menuActions = if (canRemove) listOf(remove) else emptyList(),
    )
}

@ScreenPreviews
@Composable
private fun LocalPlaylistDetailPreview() = PreviewTheme {
    LocalPlaylistDetailContent(
        name = "Road trip",
        thumbnail = null,
        songCovers = emptyList(),
        summary = listOf("6 songs", "23 minutes"),
        origin = Origin.LOCAL,
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        mode = CoverEditMode.NONE,
        arranged = null,
        reversed = false,
        onToggleOrder = {},
        onBack = {},
        onEdit = {},
        canRemove = true,
        onDelete = {},
        onAction = {},
        onSongClick = {},
        onModeChange = {},
        onMoveSong = { _, _ -> },
        onRemoveSong = {},
    )
}
