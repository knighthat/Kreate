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

package me.knighthat.kreate3.ui.screen.account

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.knighthat.kreate3.R
import me.knighthat.kreate3.download.DownloadStage
import me.knighthat.kreate3.download.DownloadWait
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.DeleteAllDownloadsButton
import me.knighthat.kreate3.ui.component.FilterIconButton
import me.knighthat.kreate3.ui.component.PlayAllButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueMoreButton
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.ShuffleButton
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SortOrderButton
import me.knighthat.kreate3.ui.component.StorageUsage
import me.knighthat.kreate3.ui.component.StorageUse
import me.knighthat.kreate3.ui.component.previewItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.viewmodel.koinViewModel

/**
 * The songs the active user downloaded whole, most recently downloaded first, under the room
 * the downloads take and the downloads not whole yet. A tap plays the list from the song.
 *
 * @param onOpenQueue opens every download not whole yet, see [DownloadQueueScreen]
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun DownloadedScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: DownloadedViewModel = koinViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val selected by viewModel.origins.collectAsStateWithLifecycle()
    val newestFirst by viewModel.newestFirst.collectAsStateWithLifecycle()
    val showDisliked by viewModel.showDisliked.collectAsStateWithLifecycle()
    val hidesDisliked by viewModel.hidesDisliked.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val unfinished by viewModel.unfinished.collectAsStateWithLifecycle()
    val waitingFor by viewModel.waitingFor.collectAsStateWithLifecycle()

    val shown: suspend () -> List<MediaItem> = { viewModel.songs.value.orEmpty().map { it.item } }
    val onPlay = rememberSongTap(radio = null, songs = shown)

    DownloadedContent(
        songs = songs,
        choices = choices,
        selected = selected,
        newestFirst = newestFirst,
        showDisliked = showDisliked,
        hidesDisliked = hidesDisliked,
        storage = storage,
        unfinished = unfinished,
        waitingFor = waitingFor,
        onBack = onBack,
        onOpenQueue = onOpenQueue,
        onToggleOrigin = viewModel::toggleOrigin,
        onToggleOrder = viewModel::toggleOrder,
        onToggleDisliked = viewModel::toggleDisliked,
        onPlay = { onPlay(it.item) },
        onQueue = rememberQueueActions(shown),
        onDeleteAll = viewModel::deleteAll,
        unfinishedActions = unfinishedActions(viewModel),
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun DownloadedContent(
    songs: List<DownloadedRow>?,
    choices: List<Origin>,
    selected: Set<Origin>,
    newestFirst: Boolean,
    showDisliked: Boolean,
    hidesDisliked: Boolean,
    storage: StorageUse?,
    unfinished: List<UnfinishedRow>,
    waitingFor: DownloadWait?,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleOrigin: (Origin) -> Unit,
    onToggleOrder: () -> Unit,
    onToggleDisliked: () -> Unit,
    onPlay: (DownloadedRow) -> Unit,
    onQueue: (QueueAction) -> Unit,
    onDeleteAll: () -> Unit,
    unfinishedActions: UnfinishedActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Delete all and the empty text speak of what's shown while some songs are hidden
    val filtered = selected.isNotEmpty() || hidesDisliked
    val listState = rememberLazyListState()

    // Another order or filter starts at the top, coming back to the page keeps the place
    val query = "$newestFirst $selected $showDisliked"
    var shownQuery by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (shownQuery != null && shownQuery != query) listState.scrollToItem(0)
        shownQuery = query
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete)
        DeleteAllDownloadsDialog(
            filtered = filtered,
            onConfirm = {
                confirmDelete = false
                onDeleteAll()
            },
            onDismiss = { confirmDelete = false },
        )

    DetailScaffold(
        title = stringResource(R.string.screen_title_downloaded),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            SortOrderButton(newestFirst, onToggleOrder)
            if (!songs.isNullOrEmpty()) DeleteAllDownloadsButton({ confirmDelete = true })
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            storage?.let {
                item(key = "storage") {
                    StorageUsage(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            if (unfinished.isNotEmpty()) {
                // The ones downloaded and the next in line, the rest are on the queue's own screen
                val next = unfinished.firstOrNull { it.download.stage == DownloadStage.QUEUED }
                val running = unfinished
                    .filter { it.download.stage == DownloadStage.DOWNLOADING || it === next }
                    .ifEmpty { unfinished.take(1) }
                item(key = "in progress") {
                    SectionTitle(
                        stringResource(R.string.section_header_in_progress),
                        onSeeMore = onOpenQueue.takeIf { running.size < unfinished.size },
                    )
                }
                waitingFor?.let { item(key = "waiting") { WaitingLine(it) } }
                items(running, key = { "unfinished:${it.download.item.mediaId}" }) {
                    UnfinishedItem(it, unfinishedActions, showBadges = true)
                }
            }

            if (songs != null && (songs.isNotEmpty() || filtered || showDisliked))
                item(key = "toolbar") {
                    DownloadedToolbar(
                        choices = choices,
                        selected = selected,
                        showDisliked = showDisliked,
                        hasSongs = songs.isNotEmpty(),
                        onToggleOrigin = onToggleOrigin,
                        onToggleDisliked = onToggleDisliked,
                        onQueue = onQueue,
                    )
                }

            if (songs?.isEmpty() == true)
                item(key = "empty") {
                    Text(
                        stringResource(
                            if (filtered) R.string.screen_downloaded_status_no_matches
                            else R.string.screen_downloaded_status_nothing_downloaded
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }

            items(songs.orEmpty(), key = { it.item.mediaId }) { row ->
                SongItem(
                    item = row.item,
                    onClick = { onPlay(row) },
                    trailing = { Text(row.file, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

/**
 * One row like the home tabs' toolbar: a chip for each origin of [choices], scrolling sideways
 * when they don't fit, then the disliked songs toggle and, with [hasSongs], the buttons queueing
 * the songs shown, which always stay at the end.
 */
@Composable
private fun DownloadedToolbar(
    choices: List<Origin>,
    selected: Set<Origin>,
    showDisliked: Boolean,
    hasSongs: Boolean,
    onToggleOrigin: (Origin) -> Unit,
    onToggleDisliked: () -> Unit,
    onQueue: (QueueAction) -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .padding(end = 4.dp),
) {
    if (choices.isNotEmpty())
        OriginChips(choices, selected, onToggleOrigin, Modifier.weight(1f))
    else Spacer(Modifier.weight(1f))

    FilterIconButton(
        R.drawable.thumb_down,
        stringResource(R.string.semantic_show_disliked_songs),
        selected = showDisliked,
        onToggle = onToggleDisliked,
    )
    if (hasSongs) {
        PlayAllButton({ onQueue(QueueAction.PLAY) })
        ShuffleButton({ onQueue(QueueAction.SHUFFLE) })
        QueueMoreButton(onQueue)
    }
}

@Composable
private fun DeleteAllDownloadsDialog(
    filtered: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_title_delete_all_downloads)) },
    text = {
        Text(
            stringResource(
                if (filtered) R.string.dialog_body_delete_all_downloads_shown
                else R.string.dialog_body_delete_all_downloads
            )
        )
    },
    confirmButton = {
        TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
)

@ScreenPreviews
@Composable
private fun DownloadedPreview() = PreviewTheme {
    DownloadedSample(
        StorageUse(
            1_400_000_000, 2_900_000_000, 4_000_000_000, 4_000_000_000,
            "1.4 GB", "2.9 GB", "4.0 GB",
        )
    )
}

/** Only this user's downloads take room, see [StorageUse.shared]. */
@ScreenPreviews
@Composable
private fun DownloadedAloneProfilePreview() = PreviewTheme {
    DownloadedSample(
        StorageUse(
            1_400_000_000, 1_400_000_000, 4_000_000_000, 4_000_000_000,
            "1.4 GB", "1.4 GB", "4.0 GB",
        )
    )
}

@Composable
private fun DownloadedSample(storage: StorageUse) {
    val files = listOf("8.4 MB · High", "6.1 MB · High", "3.2 MB · Low", "41 MB · Original")
    val rows = (previewSongs() + previewSongs(Origin.YOUTUBE)).mapIndexed { index, item ->
        // A disliked song shows only with its toggle on, marked by its badge
        val disliked = index == 2
        DownloadedRow(
            if (disliked) previewItem(
                "A disliked song",
                "Artist · Album",
                origin = Origin.OPEN_SUBSONIC,
                mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
                disliked = true,
            ) else item,
            files[index % files.size],
            disliked,
        )
    }

    DownloadedContent(
        songs = rows,
        choices = listOf(Origin.OPEN_SUBSONIC, Origin.YOUTUBE),
        selected = emptySet(),
        newestFirst = true,
        showDisliked = true,
        hidesDisliked = false,
        storage = storage,
        unfinished = previewUnfinished().take(3),
        waitingFor = null,
        onBack = {},
        onOpenQueue = {},
        onToggleOrigin = {},
        onToggleOrder = {},
        onToggleDisliked = {},
        onPlay = {},
        onQueue = {},
        onDeleteAll = {},
        unfinishedActions = UnfinishedActions({}, {}),
    )
}
