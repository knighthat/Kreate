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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.theme.CoverTheme
import org.koin.androidx.compose.koinViewModel

/**
 * A artist of this device's library.
 */
@Composable
fun LocalArtistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenDetails: (name: String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalArtistDetailViewModel = koinViewModel(),
) {
    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()

    val cover = artist?.thumbnail
    val coverModels = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }

    CoverTheme(coverModels) {
        LocalArtistDetailContent(
            name = artist?.name,
            thumbnail = artist?.thumbnail,
            origin = artist?.origin,
            albums = albums,
            songs = viewModel.songs.collectAsLazyPagingItems(),
            onBack = onBack,
            onOpenAlbum = onOpenAlbum,
            onAction = rememberQueueActions(viewModel::allSongs),
            onSongClick = rememberSongTap(null, viewModel::allSongs),
            isFollowed = isFollowed,
            onToggleFollow = viewModel::toggleFollow,
            onOpenDetails = onOpenDetails,
            onOpenStatistics = onOpenStatistics,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param isFollowed `null` turns Follow off in the menu, while it isn't known
 */
@Composable
private fun LocalArtistDetailContent(
    name: String?,
    thumbnail: Uri?,
    origin: Origin?,
    albums: List<MediaItem>,
    songs: LazyPagingItems<MediaItem>,
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    isFollowed: Boolean?,
    onToggleFollow: () -> Unit,
    onOpenDetails: (name: String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    ArtistDetailScaffold(
        name = name.orEmpty(),
        thumbnail = thumbnail,
        origin = origin,
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        menu = { onDismiss ->
            ArtistMenu(
                name = name,
                onDismiss = onDismiss,
                onAction = onAction,
                isFollowed = isFollowed,
                onToggleFollow = onToggleFollow,
                share = null,
                onOpenDetails = name?.let { { onOpenDetails(it) } },
                onOpenStatistics = onOpenStatistics,
            )
        },
    ) {
        if (albums.isNotEmpty()) {
            item {
                SectionTitle(
                    pluralStringResource(
                        R.plurals.unit_album,
                        2
                    ).replaceFirstChar { it.uppercase() })
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
        item {
            SectionTitle(
                pluralStringResource(
                    R.plurals.unit_song,
                    2
                ).replaceFirstChar { it.uppercase() })
        }
        items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
            songs[index]?.let {
                SongItem(it, onClick = { onSongClick(it) })
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
        origin = Origin.LOCAL,
        albums = previewAlbums().take(3),
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        onBack = {},
        onOpenAlbum = {},
        onAction = {},
        onSongClick = {},
        isFollowed = false,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}
