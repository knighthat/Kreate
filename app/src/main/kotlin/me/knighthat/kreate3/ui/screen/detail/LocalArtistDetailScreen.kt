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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import org.koin.androidx.compose.koinViewModel

/**
 * A artist of this device's library.
 */
@Composable
fun LocalArtistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalArtistDetailViewModel = koinViewModel(),
) {
    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val songs = viewModel.songs.collectAsLazyPagingItems()

    DetailScaffold(
        title = artist?.name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                DetailHeader(
                    uri = artist?.thumbnail,
                    title = artist?.name,
                    subtitle = null,
                    shape = CircleShape,
                )
            }
            if (albums.isNotEmpty()) {
                item {
                    SectionTitle("Albums")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(albums, key = { it.mediaId }) {
                            AlbumItem(
                                it,
                                onClick = { onOpenAlbum(it) },
                                modifier = Modifier.width(104.dp),
                            )
                        }
                    }
                }
            }
            item { SectionTitle("Songs") }
            items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                songs[index]?.let {
                    SongItem(it, onClick = {})
                }
            }
        }
    }
}
