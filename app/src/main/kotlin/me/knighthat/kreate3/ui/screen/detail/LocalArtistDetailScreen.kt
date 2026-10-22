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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.FollowButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel

/**
 * A artist of this device's library.
 */
@Composable
fun LocalArtistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalArtistDetailViewModel = koinViewModel(),
) {
    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()

    LocalArtistDetailContent(
        name = artist?.name,
        thumbnail = artist?.thumbnail,
        albums = albums,
        songs = viewModel.songs.collectAsLazyPagingItems(),
        onBack = onBack,
        onOpenAlbum = onOpenAlbum,
        onAction = rememberQueueActions(viewModel::allSongs),
        onSongClick = rememberSongTap(null, viewModel::allSongs),
        isFollowed = isFollowed,
        onToggleFollow = viewModel::toggleFollow,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param isFollowed `null` hides the follow button, while it isn't known
 */
@Composable
private fun LocalArtistDetailContent(
    name: String?,
    thumbnail: Uri?,
    albums: List<MediaItem>,
    songs: LazyPagingItems<MediaItem>,
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    isFollowed: Boolean?,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    DetailScaffold(
        title = name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            isFollowed?.let { FollowButton(it, onToggleFollow) }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = thumbnail,
                    subtitle = null,
                    shape = CircleShape,
                )
            }
            item(key = "queue-actions") { QueueActionRow(onAction) }
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
                    SongItem(it, onClick = { onSongClick(it) })
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun LocalArtistDetailPreview() = PreviewTheme {
    LocalArtistDetailContent(
        name = "Neon Coast",
        thumbnail = null,
        albums = previewAlbums().take(3),
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        onBack = {},
        onOpenAlbum = {},
        onAction = {},
        onSongClick = {},
        isFollowed = false,
        onToggleFollow = {},
    )
}
