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
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.source.local.albumArtUri
import me.knighthat.kreate3.ui.component.BookmarkButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel

/**
 * A album of this device's library.
 */
@Composable
fun LocalAlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LocalAlbumDetailViewModel = koinViewModel(),
) {
    val album by viewModel.album.collectAsState()
    val canShare by viewModel.canShare.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()

    LocalAlbumDetailContent(
        name = album?.name,
        cover = album?.let {
            it.thumbnail ?: it.id.toLongOrNull()
                ?.takeIf { _ -> it.origin == Origin.LOCAL }?.let(::albumArtUri)
        },
        subtitle = album?.let {
            listOfNotNull(
                it.artists?.joinToString(", "),
                it.year?.toString()
            ).takeIf { p -> p.isNotEmpty() }?.joinToString(" · ")
        },
        origin = album?.origin,
        isExplicit = album?.isExplicit == true,
        songs = viewModel.songs.collectAsLazyPagingItems(),
        onBack = onBack,
        onAction = rememberQueueActions(viewModel::allSongs),
        onSongClick = rememberSongTap(null, viewModel::allSongs),
        share = if (canShare) viewModel::shareLink else null,
        isBookmarked = isBookmarked,
        onToggleBookmark = viewModel::toggleBookmark,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param share gives the album's link, `null` when it has none
 * @param isBookmarked `null` hides the bookmark button, while it isn't known
 */
@Composable
private fun LocalAlbumDetailContent(
    name: String?,
    cover: Uri?,
    subtitle: String?,
    origin: Origin?,
    isExplicit: Boolean,
    songs: LazyPagingItems<MediaItem>,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend () -> Result<String>)?,
    isBookmarked: Boolean?,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    DetailScaffold(
        title = name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            isBookmarked?.let { BookmarkButton(it, onToggleBookmark) }
            if (share != null) ShareAction(name, "album", share)
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = cover,
                    subtitle = subtitle,
                    shape = MaterialTheme.shapes.medium,
                    origin = origin,
                    isExplicit = isExplicit,
                )
            }
            item(key = "queue-actions") { QueueActionRow(onAction) }
            items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                songs[index]?.let {
                    SongItem(
                        it,
                        onClick = { onSongClick(it) },
                        showThumbnail = false,
                        showAlbum = false,
                    )
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun LocalAlbumDetailPreview() = PreviewTheme {
    LocalAlbumDetailContent(
        name = "Afterglow",
        cover = null,
        subtitle = "Neon Coast · 2021",
        origin = Origin.LOCAL,
        isExplicit = true,
        songs = flowOf(PagingData.from(previewSongs())).collectAsLazyPagingItems(),
        onBack = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isBookmarked = true,
        onToggleBookmark = {},
    )
}
