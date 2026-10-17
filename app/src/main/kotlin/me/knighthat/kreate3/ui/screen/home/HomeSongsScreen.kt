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

package me.knighthat.kreate3.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.PlayAllButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueMoreButton
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.ShuffleButton
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

/**
 * Every song in the library, one per row.
 */
@Composable
fun HomeSongsScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeSongsViewModel = koinViewModel(),
) {
    val choices by viewModel.filter.choices.collectAsStateWithLifecycle()
    val selected by viewModel.filter.selected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Each visit to the tab, the songs liked on YouTube are brought up to date
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeSongsContent(
        songs = viewModel.songs.collectAsLazyPagingItems(),
        choices = choices,
        selected = selected,
        onToggleOrigin = viewModel.filter::toggle,
        onAction = rememberQueueActions(snackbar, viewModel::allSongs),
        onSongClick = rememberSongTap(
            snackbar,
            Preferences.HOME_SONGS_TAP_RADIO,
            viewModel::allSongs
        ),
        onSearch = onSearch,
        onSettings = onSettings,
        snackbar = snackbar,
        modifier = modifier,
    )
}

@Composable
private fun HomeSongsContent(
    songs: LazyPagingItems<MediaItem>,
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    HomeScaffold(
        title = Tab.SONGS.label,
        choices = choices,
        selected = selected,
        onToggleOrigin = onToggleOrigin,
        modifier = modifier,
        onSearch = onSearch,
        onSettings = onSettings,
        snackbar = snackbar,
        quickActions = {
            PlayAllButton({ onAction(QueueAction.PLAY) })
            ShuffleButton({ onAction(QueueAction.SHUFFLE) })
            QueueMoreButton(onAction)
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = songs.itemCount,
                key = songs.itemKey { it.mediaId }
            ) { index ->
                songs[index]?.let { SongItem(it, onClick = { onSongClick(it) }) }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun HomeSongsPreview() = PreviewTheme {
    HomeSongsContent(
        songs = flowOf(PagingData.from(previewSongs() + previewSongs(Origin.YOUTUBE)))
            .collectAsLazyPagingItems(),
        choices = Origin.entries,
        selected = setOf(Origin.LOCAL),
        onToggleOrigin = {},
        onAction = {},
        onSongClick = {},
        onSearch = {},
        onSettings = {},
        snackbar = remember { SnackbarHostState() },
    )
}
