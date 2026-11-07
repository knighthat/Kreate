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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
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
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.source.local.albumArtUri
import me.knighthat.kreate3.ui.component.DragHandle
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.theme.CoverTheme
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
    onOpenArtist: (Route) -> Unit,
    onOpenStatistics: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalAlbumDetailViewModel = koinViewModel(),
) {
    val action by Preferences.ALBUM_PAGE_ACTION.collectAsStateWithLifecycle()
    val album by viewModel.album.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isReordering by viewModel.isReordering.collectAsStateWithLifecycle()
    val arranged by viewModel.arranged.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val artist = remember(tracks, album) { tracks.albumArtistRoute(album?.artists) }
    val notifier = LocalNotifier.current
    val resetFailed = stringResource(R.string.error_album_reset_order)
    val moveFailed = stringResource(R.string.error_move_song)
    val orderReset = stringResource(R.string.snackbar_album_order_reset)

    val cover = album?.let {
        it.thumbnail ?: it.id.toLongOrNull()
            ?.takeIf { _ -> it.origin == Origin.LOCAL }?.let(::albumArtUri)
    }
    val coverModels = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }

    CoverTheme(coverModels) {
        LocalAlbumDetailContent(
            albumId = viewModel.albumId,
            opened = viewModel.opened,
            name = album?.name,
            cover = cover,
            artists = album?.artists?.joinToString(", "),
            year = album?.year,
            summary = tracks.takeIf { it.isNotEmpty() }?.let { songs ->
                resources.albumSummary(songs.size, songs.sumOf { it.mediaMetadata.durationMs ?: 0 })
            },
            onOpenArtist = artist?.let { { onOpenArtist(it) } },
            onOpenStatistics = onOpenStatistics,
            onEdit = onEdit,
            origin = album?.origin,
            isExplicit = album?.isExplicit == true,
            songs = viewModel.songs.collectAsLazyPagingItems(),
            isReordering = isReordering,
            arranged = arranged,
            onBack = onBack,
            action = action,
            onAction = rememberQueueActions(viewModel::allSongs),
            onSongClick = rememberSongTap(null, viewModel::allSongs),
            onReorder = viewModel::reorder,
            onResetOrder = {
                viewModel.resetOrder { reset, undo ->
                    notifier.post(
                        when {
                            !reset -> Message(resetFailed)
                            undo != null -> undoMessage(resources, orderReset, undo)
                            else -> Message(orderReset)
                        }
                    )
                }
            },
            onMoveSong = { song, after ->
                viewModel.moveSong(song.mediaId, after?.mediaId) { moved ->
                    if (!moved) notifier.post(moveFailed)
                }
            },
            share = if (canShare) viewModel::shareLink else null,
            isBookmarked = isBookmarked,
            onToggleBookmark = viewModel::toggleBookmark,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param opened shown until the album is read
 * @param arranged every song in the album's order while they are moved, see
 * [LocalAlbumDetailViewModel.arranged]
 * @param onOpenArtist opens the album's artist, `null` while there is none to open
 * @param onMoveSong moves a song to right after another one, to the start after `null`
 * @param onResetOrder puts the songs back in the album's own order
 * @param share gives the album's link, `null` when it has none
 * @param isBookmarked `null` turns Bookmark off in the menu, while it isn't known
 */
@Composable
private fun LocalAlbumDetailContent(
    albumId: String,
    opened: OpenedItem,
    name: String?,
    cover: Uri?,
    artists: String?,
    year: Int?,
    summary: String?,
    origin: Origin?,
    isExplicit: Boolean,
    songs: LazyPagingItems<MediaItem>,
    isReordering: Boolean,
    arranged: List<MediaItem>?,
    onBack: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    onOpenStatistics: () -> Unit,
    onEdit: () -> Unit,
    action: QueueAction,
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
    val listState = rememberLazyListState()
    // Changed at once by the finger, the album follows on the drop
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
    var confirmReset by remember { mutableStateOf(false) }
    val models = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }

    AlbumDetailScaffold(
        name = (name ?: opened.name).orEmpty(),
        albumId = albumId,
        models = models,
        standIn = opened.picture,
        artists = artists,
        year = year,
        summary = summary,
        origin = origin,
        isExplicit = isExplicit,
        description = null,
        onBack = onBack,
        onOpenArtist = onOpenArtist,
        modifier = modifier,
        contentPadding = contentPadding,
        listState = listState,
        action = action,
        onAction = onAction.takeIf { songs.itemCount > 0 },
        menu = { onDismiss ->
            AlbumMenu(
                name = name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { songs.itemCount > 0 },
                isBookmarked = isBookmarked,
                onToggleBookmark = onToggleBookmark,
                share = share,
                onOpenArtist = onOpenArtist,
                onOpenStatistics = onOpenStatistics,
                onEdit = onEdit,
                // A remote album keeps the order of its server
                isReordering = isReordering.takeIf { origin == Origin.LOCAL },
                canReorder = songs.itemCount > 1,
                onReorder = { onReorder(!isReordering) },
                onResetOrder = { confirmReset = true },
            )
        },
    ) {
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
                        showBadges = false,
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
                songs[index]?.let { AlbumTrack(it, index + 1, onClick = { onSongClick(it) }) }
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

@ScreenPreviews
@Composable
private fun LocalAlbumDetailPreview() = PreviewTheme {
    LocalAlbumDetailContent(
        albumId = "",
        opened = OpenedItem("Afterglow", null),
        name = "Afterglow",
        cover = null,
        artists = "Neon Coast",
        year = 2021,
        summary = "6 songs · 23 minutes",
        origin = Origin.LOCAL,
        isExplicit = true,
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        isReordering = false,
        arranged = null,
        onBack = {},
        onOpenArtist = {},
        onOpenStatistics = {},
        onEdit = {},
        action = QueueAction.PLAY,
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
