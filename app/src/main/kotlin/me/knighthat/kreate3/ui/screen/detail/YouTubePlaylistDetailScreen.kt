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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

/** Songs from the end of the list at which the next ones are asked for. */
private const val PREFETCH_DISTANCE = 10

/**
 * A playlist of YouTube, fetched when the screen opens and never stored. Its songs come part after
 * part as the list scrolls.
 */
@Composable
fun YouTubePlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: YouTubePlaylistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val edit = viewModel.edit.collectState(preparing)
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val deleteFailed = stringResource(R.string.error_playlist_delete)
    val name = (state as? LiveState.Ready)?.value?.name
    val deleted = stringResource(
        R.string.snackbar_playlist_deleted,
        name?.let { stringResource(R.string.quoted, it) }
            ?: stringResource(R.string.snackbar_this_playlist)
    )
    val actions = remember(viewModel, notifier, resources, deleted, deleteFailed, onBack) {
        PlaylistEditActions(
            onArrange = viewModel.edit::arrange,
            onModeChange = { new ->
                if (new == CoverEditMode.NONE) {
                    viewModel.saveChanges { _, message -> notifier.post(message) }
                } else {
                    viewModel.startEditing(new) { notifier.post(it) }
                }
            },
            onRemoved = { song, undo -> notifier.post(removedMessage(resources, song, undo)) },
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

    val cover = (state as? LiveState.Ready)?.value?.thumbnail
    val firstCovers = songs
    val coverModels = remember(cover, firstCovers) {
        listOfNotNull(thumbnailModel(cover, null)).ifEmpty { firstCovers.songCovers().take(1) }
    }

    viewModel.RefreshOnPull(enabled = !edit.editing) {
        CoverTheme(coverModels) {
            YouTubePlaylistDetailContent(
                state = state,
                songs = songs,
                hasMore = hasMore,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::load,
                onBack = onBack,
                onAction = rememberQueueActions { viewModel.songs.value },
                onSongClick = rememberSongTap(null) { viewModel.songs.value },
                share = viewModel::shareLink,
                edit = edit,
                actions = actions,
                modifier = modifier,
                contentPadding = contentPadding,
            )
        }
    }
}

/**
 * @param songs the songs fetched so far
 * @param hasMore whether YouTube has songs after [songs], [onLoadMore] fetches them
 * @param edit where moving and taking out songs stands
 * @param actions what the user can do about it
 */
@Composable
private fun YouTubePlaylistDetailContent(
    state: LiveState<YouTubePlaylistDetail>,
    songs: List<MediaItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: suspend () -> Result<String>,
    edit: PlaylistEditState,
    actions: PlaylistEditActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val playlist = (state as? LiveState.Ready)?.value
    val listState = rememberLazyListState()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = rememberPlaylistEditing(edit, actions, listState, contentPadding)
    // Changes not sent are not lost without asking; while a request waits, back waits
    PlaylistEditBackHandler(edit) { confirmLeave = true }
    val cover = playlist?.thumbnail
    val models = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }
    val songCovers = remember(songs) { songs.songCovers() }

    PlaylistDetailScaffold(
        name = playlist?.name.orEmpty(),
        models = models,
        songCovers = songCovers,
        owner = playlist?.subtitle,
        // Its songs come part after part, YouTube says how many and how long
        summary = playlist?.summary,
        origin = Origin.YOUTUBE,
        onBack = { if (edit.dirty) confirmLeave = true else onBack() },
        onAction = onAction.takeIf { songs.isNotEmpty() },
        share = share,
        modifier = modifier,
        contentPadding = contentPadding,
        listState = listState,
        menu = { onDismiss ->
            PlaylistMenu(
                name = playlist?.name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { songs.isNotEmpty() },
                share = share,
                mode = edit.mode,
                // Stays on while the last song is taken out, so it can be turned off
                canEdit = playlist?.editable == true &&
                        (edit.mode != CoverEditMode.NONE || songs.isNotEmpty()),
                onModeChange = actions.onModeChange,
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
            return@PlaylistDetailScaffold
        }
        itemsIndexed(songs, key = { _, it -> it.mediaId }) { index, song ->
            if (index >= songs.size - PREFETCH_DISTANCE) LaunchedEffect(songs.size) { onLoadMore() }
            SongItem(song, onClick = { onSongClick(song) })
        }
        if (songs.isEmpty() && !hasMore)
            item(key = "empty") { EmptyPlaylist() }
        if (hasMore)
            item(key = "more") {
                LaunchedEffect(songs.size) { onLoadMore() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) { CircularProgressIndicator() }
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
private fun YouTubePlaylistDetailPreview() = PreviewTheme {
    YouTubePlaylistDetailContent(
        state = LiveState.Ready(
            YouTubePlaylistDetail(
                name = "Road trip",
                thumbnail = null,
                subtitle = "Neon Coast",
                summary = "42 songs • 2 hours",
                editable = true,
            )
        ),
        songs = previewSongs(Origin.YOUTUBE),
        hasMore = true,
        onLoadMore = {},
        onRetry = {},
        onBack = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        edit = PlaylistEditState(),
        actions = PlaylistEditActions(),
    )
}
