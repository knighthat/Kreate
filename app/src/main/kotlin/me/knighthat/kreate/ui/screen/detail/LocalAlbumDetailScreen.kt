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

package me.knighthat.kreate.ui.screen.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.media.albumArtUri
import me.knighthat.kreate.ui.component.SongItem
import org.koin.androidx.compose.koinViewModel

/**
 * A album of this device's library.
 */
@Composable
fun LocalAlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalAlbumDetailViewModel = koinViewModel(),
) {
    val album by viewModel.album.collectAsState()
    val songs = viewModel.songs.collectAsLazyPagingItems()

    DetailScaffold(album?.name.orEmpty(), onBack, modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                DetailHeader(
                    uri = album?.let {
                        it.thumbnail ?: it.id.toLongOrNull()
                            ?.takeIf { _ -> it.origin == Origin.LOCAL }?.let(::albumArtUri)
                    },
                    title = album?.name,
                    subtitle = album?.let {
                        listOfNotNull(
                            it.artists?.joinToString(", "),
                            it.year?.toString()
                        ).takeIf { p -> p.isNotEmpty() }?.joinToString(" · ")
                    },
                    shape = MaterialTheme.shapes.medium,
                )
            }
            items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                songs[index]?.let {
                    SongItem(it, onClick = {}, showThumbnail = false)
                }
            }
        }
    }
}
