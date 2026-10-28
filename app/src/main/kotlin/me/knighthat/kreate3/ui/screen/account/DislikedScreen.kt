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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.component.FilterIconButton
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SortOrderButton
import me.knighthat.kreate3.ui.component.UndislikeAllButton
import me.knighthat.kreate3.ui.component.icon
import me.knighthat.kreate3.ui.component.playTapped
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.sourceRes
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The songs the active user disliked, which every other list hides, each with how long ago it
 * was disliked, most recently disliked first. A tap plays the song alone.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun DislikedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: DislikedViewModel = koinViewModel(),
) {
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val selected by viewModel.origins.collectAsStateWithLifecycle()
    val newestFirst by viewModel.newestFirst.collectAsStateWithLifecycle()

    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    DislikedContent(
        songs = viewModel.songs.collectAsLazyPagingItems(),
        choices = choices,
        selected = selected,
        newestFirst = newestFirst,
        onBack = onBack,
        onToggleOrigin = viewModel::toggleOrigin,
        onToggleOrder = viewModel::toggleOrder,
        onPlay = { row ->
            scope.launch {
                withContext(Dispatchers.Default) {
                    playback.playTapped(resources, row.item, listOf(row.item), radio = false)
                }?.let(notifier::post)
            }
        },
        onUndislikeAll = { viewModel.undislikeAll(notifier::post) },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun DislikedContent(
    songs: LazyPagingItems<DislikedRow>,
    choices: List<Origin>,
    selected: Set<Origin>,
    newestFirst: Boolean,
    onBack: () -> Unit,
    onToggleOrigin: (Origin) -> Unit,
    onToggleOrder: () -> Unit,
    onPlay: (DislikedRow) -> Unit,
    onUndislikeAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val filtered = selected.isNotEmpty()
    val empty = songs.loadState.refresh is LoadState.NotLoading && songs.itemCount == 0
    val listState = rememberLazyListState()

    // Another order or filter starts at the top, coming back to the page keeps the place
    val query = if (songs.itemCount > 0) songs.peek(0)?.query else null
    var shownQuery by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(query) {
        if (query == null || query == shownQuery) return@LaunchedEffect
        if (shownQuery != null) listState.scrollToItem(0)
        shownQuery = query
    }
    var confirmUndislike by rememberSaveable { mutableStateOf(false) }

    if (confirmUndislike)
        UndislikeAllDialog(
            filtered = filtered,
            onConfirm = {
                confirmUndislike = false
                onUndislikeAll()
            },
            onDismiss = { confirmUndislike = false },
        )

    DetailScaffold(
        title = stringResource(R.string.screen_title_disliked),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            SortOrderButton(newestFirst, onToggleOrder)
            if (!empty) UndislikeAllButton({ confirmUndislike = true })
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            if (choices.isNotEmpty())
                OriginChips(choices, selected, onToggleOrigin)

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (empty)
                    item(key = "empty") {
                        Text(
                            stringResource(
                                if (filtered) R.string.screen_disliked_status_no_matches
                                else R.string.screen_disliked_status_nothing_disliked
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillParentMaxSize()
                                .wrapContentSize(Alignment.Center),
                        )
                    }

                items(
                    count = songs.itemCount,
                    key = songs.itemKey { "${it.query}:${it.item.mediaId}" },
                ) { index ->
                    songs[index]?.let { row ->
                        SongItem(
                            item = row.item,
                            onClick = { onPlay(row) },
                            trailing = {
                                Text(row.time, style = MaterialTheme.typography.labelMedium)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A chip for each origin of [choices], scrolling sideways when they don't fit. */
@Composable
private fun OriginChips(
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
) {
    val iconOnly = LocalIconOnly.current
    choices.forEach {
        if (iconOnly)
            FilterIconButton(
                it.icon,
                stringResource(it.sourceRes),
                selected = it in selected,
                onToggle = { onToggleOrigin(it) },
            )
        else FilterChip(
            selected = it in selected,
            onClick = { onToggleOrigin(it) },
            label = { Text(stringResource(it.sourceRes)) },
            leadingIcon = {
                // The label says the same, nothing more for screen readers
                Icon(
                    painter = painterResource(it.icon),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun UndislikeAllDialog(filtered: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_undislike_all)) },
        text = {
            Text(
                stringResource(
                    if (filtered) R.string.dialog_body_undislike_all_shown
                    else R.string.dialog_body_undislike_all
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_undislike)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

@ScreenPreviews
@Composable
private fun DislikedPreview() = PreviewTheme { DislikedSample() }

/** The origin chips as their icons alone, see [LocalIconOnly]. */
@ScreenPreviews
@Composable
private fun DislikedIconOnlyPreview() = PreviewTheme {
    CompositionLocalProvider(LocalIconOnly provides true) { DislikedSample() }
}

@Composable
private fun DislikedSample() {
    val times = listOf("5 min. ago", "2 hr. ago", "Yesterday", "3 days ago", "Oct 2")
    val rows = (previewSongs() + previewSongs(Origin.YOUTUBE)).mapIndexed { index, item ->
        DislikedRow(item, times[index % times.size], query = 1)
    }

    DislikedContent(
        songs = flowOf(PagingData.from(rows)).collectAsLazyPagingItems(),
        choices = listOf(Origin.LOCAL, Origin.YOUTUBE),
        selected = setOf(Origin.LOCAL),
        newestFirst = true,
        onBack = {},
        onToggleOrigin = {},
        onToggleOrder = {},
        onPlay = {},
        onUndislikeAll = {},
    )
}
