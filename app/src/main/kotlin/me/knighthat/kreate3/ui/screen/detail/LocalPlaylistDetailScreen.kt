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

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SongMenuAction
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel

/**
 * A playlist of this device's library.
 */
@Composable
fun LocalPlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalPlaylistDetailViewModel = koinViewModel(),
) {
    val playlist by viewModel.playlist.collectAsState()
    val notifier = LocalNotifier.current

    LocalPlaylistDetailContent(
        name = playlist?.name,
        thumbnail = playlist?.thumbnail,
        subtitle = playlist?.let { if (it.origin == Origin.LOCAL) "On this device" else it.origin.name },
        songs = viewModel.songs.collectAsLazyPagingItems(),
        onBack = onBack,
        onAction = rememberQueueActions(viewModel::allSongs),
        onSongClick = rememberSongTap(null, viewModel::allSongs),
        onRemoveSong = { song ->
            viewModel.removeSong(song.mediaId) { removed ->
                notifier.post(
                    if (removed) "Removed from ${playlist?.name ?: "the playlist"}"
                    else "Couldn't remove it from the playlist"
                )
            }
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun LocalPlaylistDetailContent(
    name: String?,
    thumbnail: Uri?,
    subtitle: String?,
    songs: LazyPagingItems<MediaItem>,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onRemoveSong: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    DetailScaffold(
        title = name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = thumbnail,
                    subtitle = subtitle,
                    shape = MaterialTheme.shapes.medium,
                )
            }
            item(key = "queue-actions") { QueueActionRow(onAction) }
            items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                songs[index]?.let {
                    val remove = remember(it, onRemoveSong) {
                        SongMenuAction("Remove from playlist", R.drawable.playlist_remove) {
                            onRemoveSong(it)
                        }
                    }
                    SongItem(it, onClick = { onSongClick(it) }, menuActions = listOf(remove))
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun LocalPlaylistDetailPreview() = PreviewTheme {
    LocalPlaylistDetailContent(
        name = "Road trip",
        thumbnail = null,
        subtitle = "On this device",
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        onBack = {},
        onAction = {},
        onSongClick = {},
        onRemoveSong = {},
    )
}
