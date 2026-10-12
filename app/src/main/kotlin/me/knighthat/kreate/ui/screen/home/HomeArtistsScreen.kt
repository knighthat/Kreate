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

package me.knighthat.kreate.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate.ui.component.ArtistItem
import me.knighthat.kreate.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel


private const val ARTIST_ITEM_SIZE = 100

/**
 * Every artist in the library, as many to a row as fit.
 */
@Composable
fun HomeArtistsScreen(
    onOpen: (mediaId: String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeArtistsViewModel = koinViewModel(),
) {
    val artists = viewModel.artists.collectAsLazyPagingItems()

    HomeScaffold(Tab.ARTISTS.label, modifier, onSearch = onSearch) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ARTIST_ITEM_SIZE.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            items(count = artists.itemCount, key = artists.itemKey { it.mediaId }) { index ->
                artists[index]?.let { ArtistItem(it, onClick = { onOpen(it.mediaId) }) }
            }
        }
    }
}
