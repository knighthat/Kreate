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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.theme.CoverTheme
import org.koin.androidx.compose.koinViewModel

/**
 * A playlist of the OpenSubsonic server, fetched when the screen opens and never stored. Its
 * songs can be moved and taken out on the page, the server is told when that is turned off.
 */
@Composable
fun OpenSubsonicPlaylistDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicPlaylistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val edit = viewModel.edit.collectState()
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
    val actions = remember(viewModel, notifier, resources, deleted, deleteFailed, onBack) {
        PlaylistEditActions(
            onArrange = viewModel.edit::arrange,
            onModeChange = { new ->
                if (new == CoverEditMode.NONE) {
                    viewModel.saveChanges { _, message -> notifier.post(message) }
                } else {
                    viewModel.edit.start(new)
                }
            },
            onRemoved = { song, undo ->
                notifier.post(removedMessage(resources, song, undo))
            },
            onAskDelete = viewModel.edit::askDelete,
            onCancelDelete = viewModel.edit::cancelDelete,
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
                viewModel.edit.discard()
                onBack()
            },
        )
    }

    viewModel.RefreshOnPull(enabled = !edit.editing) {
        CoverTheme(coverModels) {
            OpenSubsonicPlaylistDetailContent(
                state = state,
                onRetry = viewModel::load,
                onBack = onBack,
                onEdit = onEdit,
                onAction = rememberQueueActions {
                    (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
                },
                onSongClick = rememberSongTap(null) {
                    (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
                },
                share = if (canShare) viewModel::shareLink else null,
                edit = edit,
                actions = actions,
                modifier = modifier,
                contentPadding = contentPadding,
            )
        }
    }
}

/**
 * @param share gives a link to the playlist, `null` while the user may not share
 * @param edit where moving and taking out songs stands
 * @param actions what the user can do about it
 */
@Composable
private fun OpenSubsonicPlaylistDetailContent(
    state: LiveState<OpenSubsonicPlaylistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
    edit: PlaylistEditState,
    actions: PlaylistEditActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val playlist = (state as? LiveState.Ready)?.value
    val resources = LocalResources.current
    val listState = rememberLazyListState()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = rememberPlaylistEditing(edit, actions, listState, contentPadding)

    // Changes not sent are not lost without asking; while a request waits, back waits
    PlaylistEditBackHandler(edit) { confirmLeave = true }
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
        onBack = { if (edit.dirty) confirmLeave = true else onBack() },
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
                mode = edit.mode,
                // Stays on while the last song is taken out, so it can be turned off
                canEdit = playlist?.editable == true &&
                        (edit.mode != CoverEditMode.NONE || playlist.songs.isNotEmpty()),
                onModeChange = actions.onModeChange,
                onEdit = onEdit.takeIf { playlist?.editable == true },
                onDelete = actions.onAskDelete,
                canDelete = playlist?.editable == true,
                busy = edit.busy,
            )
        },
    ) {
        if (playlist == null) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@PlaylistDetailScaffold
        }
        if (editing.active) {
            with(editing) { rows(onSongClick) }
            if (edit.arranging?.isEmpty() == true) item(key = "empty") { EmptyPlaylist() }
        } else {
            items(playlist.songs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
            if (playlist.songs.isEmpty())
                item(key = "empty") { EmptyPlaylist() }
        }
    }

    if (edit.confirmingDelete)
        PlaylistDeleteDialog(
            name = playlist?.name.orEmpty(),
            deleting = edit.deleting,
            onDelete = actions.onDelete,
            onCancel = actions.onCancelDelete,
        )

    if (confirmLeave)
        PlaylistLeaveDialog(
            onSave = {
                confirmLeave = false
                actions.onSaveAndLeave()
            },
            onDiscard = {
                confirmLeave = false
                actions.onDiscardAndLeave()
            },
            onClose = { confirmLeave = false },
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
        onEdit = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        edit = PlaylistEditState(),
        actions = PlaylistEditActions(),
    )
}
