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
import me.knighthat.kreate.ui.component.AlbumItem
import me.knighthat.kreate.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel


private const val ALBUM_ITEM_SIZE = 120

/**
 * Every album in the library, as many to a row as fit.
 */
@Composable
fun HomeAlbumsScreen(
    onOpen: (mediaId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeAlbumsViewModel = koinViewModel(),
) {
    val albums = viewModel.albums.collectAsLazyPagingItems()

    HomeScaffold(Tab.ALBUMS.label, modifier) { padding ->
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
                albums[index]?.let { AlbumItem(it, onClick = { onOpen(it.mediaId) }) }
            }
        }
    }
}
