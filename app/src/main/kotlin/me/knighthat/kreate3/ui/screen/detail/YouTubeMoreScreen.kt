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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.ArtistItem
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

private const val ITEM_SIZE = 120

/**
 * Every item of a row of YouTube, like all the singles of an artist, as a grid. Fetched as it
 * scrolls and never stored.
 */
@Composable
fun YouTubeMoreScreen(
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeMoreViewModel = koinViewModel(),
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val refresh = items.loadState.refresh

    DetailScaffold(viewModel.title, onBack, modifier) { innerPadding ->
        if (items.itemCount == 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                when (refresh) {
                    is LoadState.Loading -> CircularProgressIndicator()
                    is LoadState.Error -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Can't reach YouTube",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = items::retry) { Text("Try again") }
                    }

                    else -> Text(
                        text = "Nothing here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@DetailScaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ITEM_SIZE.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { (tab, item) -> "${tab.name}:${item.mediaId}" },
            ) { index ->
                items[index]?.let { (tab, item) ->
                    if (tab == Tab.ARTISTS) ArtistItem(item, onClick = { onOpen(tab, item) })
                    else AlbumItem(item, onClick = { onOpen(tab, item) })
                }
            }
        }
    }
}
