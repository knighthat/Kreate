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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate.ui.component.SongItem
import me.knighthat.kreate.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

/**
 * Every song in the library, one per row.
 */
@Composable
fun HomeSongsScreen(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeSongsViewModel = koinViewModel(),
) {
    val songs = viewModel.songs.collectAsLazyPagingItems()

    HomeScaffold(Tab.SONGS.label, modifier, onSearch = onSearch) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = songs.itemCount,
                key = songs.itemKey { it.mediaId }
            ) { index ->
                songs[index]?.let { SongItem(it, onClick = {}) }
            }
        }
    }
}
