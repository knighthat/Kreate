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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.RandomButton
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.previewAlbums
import org.koin.androidx.compose.koinViewModel


private const val ALBUM_ITEM_SIZE = 120

/**
 * Every album in the library, as many to a row as fit.
 */
@Composable
fun HomeAlbumsScreen(
    onOpen: (MediaItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeAlbumsViewModel = koinViewModel(),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val choices by viewModel.filter.choices.collectAsStateWithLifecycle()
    val selected by viewModel.filter.selected.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    // Each visit to the tab, the items of the external sources are fetched again
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeAlbumsContent(
        albums = viewModel.albums.collectAsLazyPagingItems(),
        live = live,
        choices = choices,
        selected = selected,
        onToggleOrigin = viewModel.filter::toggle,
        onOpen = onOpen,
        onRandom = {
            scope.launch {
                val picked = viewModel.random()
                if (picked != null) onOpen(picked)
                else notifier.post("No albums to pick from")
            }
        },
        onSearch = onSearch,
        onSettings = onSettings,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param live the albums of the external sources, after the ones of the database
 */
@Composable
private fun HomeAlbumsContent(
    albums: LazyPagingItems<MediaItem>,
    live: List<MediaItem>,
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRandom: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    HomeScaffold(
        title = stringResource(R.string.screen_title_albums),
        choices = choices,
        selected = selected,
        onToggleOrigin = onToggleOrigin,
        modifier = modifier,
        contentPadding = contentPadding,
        onSearch = onSearch,
        onSettings = onSettings,
        quickActions = { RandomButton(onRandom) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ALBUM_ITEM_SIZE.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            items(count = albums.itemCount, key = albums.itemKey { it.mediaId }) { index ->
                albums[index]?.let { AlbumItem(it, onClick = { onOpen(it) }) }
            }
            items(live, key = { it.liveKey }) {
                AlbumItem(it, onClick = { onOpen(it) })
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun HomeAlbumsPreview() = PreviewTheme {
    HomeAlbumsContent(
        albums = flowOf(PagingData.from(previewAlbums())).collectAsLazyPagingItems(),
        live = previewAlbums(Origin.YOUTUBE).take(3),
        choices = Origin.entries,
        selected = emptySet(),
        onToggleOrigin = {},
        onOpen = {},
        onRandom = {},
        onSearch = {},
        onSettings = {},
    )
}
