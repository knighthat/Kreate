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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.undoMessage
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A playlist of the OpenSubsonic server, fetched when the screen opens and never stored. Its
 * songs can be moved and taken out on the page, the server is told when that is turned off.
 */
@Composable
fun OpenSubsonicPlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicPlaylistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val arranging by viewModel.arranging.collectAsStateWithLifecycle()
    val mode by viewModel.editMode.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val confirmingDelete by viewModel.confirmingDelete.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val deleteFailed = stringResource(R.string.error_playlist_delete)
    val name = (state as? LiveState.Ready)?.value?.name
    val deleted = stringResource(
        R.string.snackbar_playlist_deleted,
        name?.let { stringResource(R.string.quoted, it) }
            ?: stringResource(R.string.snackbar_this_playlist)
    )

    val cover = (state as? LiveState.Ready)?.value?.thumbnail
    val firstCovers = (state as? LiveState.Ready)?.value?.songs.orEmpty()
    val coverModels = remember(cover, firstCovers) {
        listOfNotNull(thumbnailModel(cover, null)).ifEmpty { firstCovers.songCovers().take(1) }
    }

    CoverTheme(coverModels) {
        OpenSubsonicPlaylistDetailContent(
            state = state,
            onRetry = viewModel::load,
            onBack = onBack,
            onAction = rememberQueueActions {
                (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
            },
            onSongClick = rememberSongTap(null) {
                (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
            },
            share = if (canShare) viewModel::shareLink else null,
            arranging = arranging,
            mode = mode,
            saving = saving,
            deleting = deleting,
            confirmingDelete = confirmingDelete,
            onAskDelete = viewModel::askDelete,
            onCancelDelete = viewModel::cancelDelete,
            dirty = arranging != null && viewModel.hasChanges(),
            onArrange = viewModel::arrange,
            onModeChange = { new ->
                if (new == CoverEditMode.NONE) {
                    viewModel.saveChanges { _, message -> notifier.post(message) }
                } else {
                    viewModel.edit(new)
                }
            },
            onRemoved = { song, undo ->
                // The song is gone from the page, its name says what was removed
                val removedFrom = resources.getString(
                    R.string.snackbar_removed_from_playlist,
                    songsLabel(resources, listOf(song))
                )
                notifier.post(undoMessage(resources, removedFrom, undo))
            },
            onDelete = {
                viewModel.deletePlaylist { done ->
                    if (done) {
                        notifier.post(deleted)
                        onBack()
                    } else {
                        notifier.post(deleteFailed)
                    }
                }
            },
            onSaveAndLeave = {
                viewModel.saveChanges { saved, message ->
                    notifier.post(message)
                    if (saved) onBack()
                }
            },
            onDiscardAndLeave = {
                viewModel.discardChanges()
                onBack()
            },
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param share gives a link to the playlist, `null` while the user may not share
 * @param arranging the songs as the user left them while the playlist is edited, `null`
 * otherwise, see [OpenSubsonicPlaylistDetailViewModel.arranging]
 * @param mode what the covers do while it is edited
 * @param saving whether the changes are on their way to the server
 * @param deleting whether the playlist is being deleted on the server, the dialog waits
 * @param confirmingDelete whether the dialog that asks to delete it shows
 * @param onAskDelete shows that dialog
 * @param onCancelDelete closes it
 * @param dirty whether there are changes not sent yet, leaving then asks first
 * @param onArrange gets the songs after each change
 * @param onModeChange switches the covers, or sends the changes with [CoverEditMode.NONE]
 * @param onRemoved a song was taken out of the list, with how to put it back
 * @param onDelete deletes the playlist on the server, once the user confirmed
 * @param onSaveAndLeave sends the changes, then leaves the page
 * @param onDiscardAndLeave leaves the page without sending anything
 */
@Composable
private fun OpenSubsonicPlaylistDetailContent(
    state: LiveState<OpenSubsonicPlaylistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
    arranging: List<MediaItem>?,
    mode: CoverEditMode,
    saving: Boolean,
    deleting: Boolean,
    confirmingDelete: Boolean,
    onAskDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    dirty: Boolean,
    onArrange: (List<MediaItem>) -> Unit,
    onModeChange: (CoverEditMode) -> Unit,
    onRemoved: (song: MediaItem, undo: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    onSaveAndLeave: () -> Unit,
    onDiscardAndLeave: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val playlist = (state as? LiveState.Ready)?.value
    val resources = LocalResources.current
    val editing = arranging != null
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.menu_remove_from_playlist)
    // The user's changes are made here at once, the view model is told after each
    var entries by remember(editing) { mutableStateOf(arranging) }
    val reorder = rememberReorderableLazyListState(listState, contentPadding) { from, to ->
        // Their indices count the header too, the songs are found by key
        entries = entries?.toMutableList()?.apply {
            val fromIndex = indexOfFirst { it.mediaId == from.key }
            val toIndex = indexOfFirst { it.mediaId == to.key }
            if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
        }
        entries?.let(onArrange)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val lifted = MaterialTheme.colorScheme.surfaceContainerHighest

    fun remove(song: MediaItem) {
        val list = entries ?: return
        val index = list.indexOfFirst { it.mediaId == song.mediaId }
        if (index < 0) return
        entries = list.toMutableList().apply { removeAt(index) }.also(onArrange)
        onRemoved(song) {
            val now = entries ?: return@onRemoved
            if (now.none { it.mediaId == song.mediaId }) {
                entries = now.toMutableList().apply { add(index.coerceAtMost(size), song) }
                    .also(onArrange)
            }
        }
    }

    // Changes not sent are not lost without asking; while they are on their way, back waits
    BackHandler(enabled = dirty || saving || deleting) {
        if (!saving && !deleting) confirmLeave = true
    }
    val cover = playlist?.thumbnail
    val models = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }
    val songCovers = remember(playlist?.songs) { playlist?.songs.orEmpty().songCovers() }

    PlaylistDetailScaffold(
        name = playlist?.name.orEmpty(),
        models = models,
        songCovers = songCovers,
        owner = null,
        summary = playlist?.songs?.takeIf { it.isNotEmpty() }?.let { songs ->
            resources.albumSummary(songs.size, songs.sumOf { it.mediaMetadata.durationMs ?: 0 })
        },
        origin = Origin.OPEN_SUBSONIC,
        onBack = { if (dirty) confirmLeave = true else onBack() },
        onAction = onAction.takeIf { playlist?.songs?.isNotEmpty() == true },
        share = if (share != null && playlist != null) ({ share(playlist.name) }) else null,
        modifier = modifier,
        contentPadding = contentPadding,
        listState = listState,
        menu = { onDismiss ->
            PlaylistMenu(
                name = playlist?.name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { playlist?.songs?.isNotEmpty() == true },
                share = if (share != null && playlist != null) ({ share(playlist.name) }) else null,
                mode = mode,
                // Stays on while the last song is taken out, so it can be turned off
                canEdit = playlist?.editable == true &&
                        (mode != CoverEditMode.NONE || playlist.songs.isNotEmpty()),
                onModeChange = onModeChange,
                onDelete = onAskDelete,
                canDelete = playlist?.editable == true,
                saving = saving,
            )
        },
    ) {
        if (playlist == null) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@PlaylistDetailScaffold
        }
        val moving = entries
        if (moving != null) {
            items(moving, key = { it.mediaId }) { song ->
                ReorderableItem(reorder, key = song.mediaId) { isDragging ->
                    SongItem(
                        item = song,
                        onClick = { onSongClick(song) },
                        // Opaque, the songs it passes over don't show through it
                        modifier = if (isDragging) Modifier.background(lifted) else Modifier,
                        thumbnailOverlay = {
                            CoverEditOverlay(
                                mode = mode,
                                handle = Modifier.draggableHandle(
                                    enabled = !saving,
                                    onDragStarted = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    },
                                ),
                                removeDescription = stringResource(
                                    R.string.semantic_remove_from_the_playlist
                                ),
                                onRemove = { if (!saving) remove(song) },
                            )
                        },
                        menuActions = listOf(
                            SongMenuAction(removeLabel, R.drawable.playlist_remove) {
                                if (!saving) remove(song)
                            }
                        ),
                    )
                }
            }
            if (moving.isEmpty()) item(key = "empty") { EmptyPlaylist() }
        } else {
            items(playlist.songs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
            if (playlist.songs.isEmpty())
                item(key = "empty") { EmptyPlaylist() }
        }
    }

    if (confirmingDelete)
        AlertDialog(
            // Not while the server is deleting, the dialog goes when it answered
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.dialog_title_delete_playlist)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_body_delete_playlist,
                        stringResource(R.string.quoted, playlist?.name.orEmpty())
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete, enabled = !deleting) {
                    if (deleting)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    else
                        Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete, enabled = !deleting) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

    if (confirmLeave)
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.dialog_title_unsaved_changes)) },
            text = { Text(stringResource(R.string.dialog_body_unsaved_changes)) },
            // One under the other, three labels don't fit side by side
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        confirmLeave = false
                        onSaveAndLeave()
                    }) { Text(stringResource(R.string.action_save_changes)) }
                    TextButton(onClick = {
                        confirmLeave = false
                        onDiscardAndLeave()
                    }) { Text(stringResource(R.string.action_discard_changes)) }
                    TextButton(onClick = { confirmLeave = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            },
        )
}

@ScreenPreviews
@Composable
private fun OpenSubsonicPlaylistDetailPreview() = PreviewTheme {
    OpenSubsonicPlaylistDetailContent(
        state = LiveState.Ready(
            OpenSubsonicPlaylistDetail(
                name = "Road trip",
                thumbnail = null,
                songs = previewSongs(Origin.OPEN_SUBSONIC),
                editable = true,
            )
        ),
        onRetry = {},
        onBack = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        arranging = null,
        mode = CoverEditMode.NONE,
        saving = false,
        deleting = false,
        confirmingDelete = false,
        onAskDelete = {},
        onCancelDelete = {},
        dirty = false,
        onArrange = {},
        onModeChange = {},
        onRemoved = { _, _ -> },
        onDelete = {},
        onSaveAndLeave = {},
        onDiscardAndLeave = {},
    )
}
