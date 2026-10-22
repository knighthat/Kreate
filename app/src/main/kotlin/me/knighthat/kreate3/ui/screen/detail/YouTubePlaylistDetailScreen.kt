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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel

/** Songs from the end of the list at which the next ones are asked for. */
private const val PREFETCH_DISTANCE = 10

/**
 * A playlist of YouTube, fetched when the screen opens and never stored. Its songs come part after
 * part as the list scrolls.
 */
@Composable
fun YouTubePlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: YouTubePlaylistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()

    YouTubePlaylistDetailContent(
        state = state,
        songs = songs,
        hasMore = hasMore,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        onBack = onBack,
        onAction = rememberQueueActions { viewModel.songs.value },
        onSongClick = rememberSongTap(null) { viewModel.songs.value },
        share = viewModel::shareLink,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param songs the songs fetched so far
 * @param hasMore whether YouTube has songs after [songs], [onLoadMore] fetches them
 */
@Composable
private fun YouTubePlaylistDetailContent(
    state: LiveState<YouTubePlaylistDetail>,
    songs: List<MediaItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: suspend () -> Result<String>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val playlist = (state as? LiveState.Ready)?.value

    LiveDetailScreen(
        title = playlist?.name.orEmpty(),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = { ShareAction(playlist?.name, "playlist", share) },
    ) { detail, innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = detail.thumbnail,
                    subtitle = detail.subtitle,
                    shape = MaterialTheme.shapes.medium,
                    origin = Origin.YOUTUBE,
                )
            }
            item(key = "queue-actions") { QueueActionRow(onAction) }
            itemsIndexed(songs, key = { _, it -> it.mediaId }) { index, song ->
                if (index >= songs.size - PREFETCH_DISTANCE) LaunchedEffect(songs.size) { onLoadMore() }
                SongItem(song, onClick = { onSongClick(song) })
            }
            if (songs.isEmpty() && !hasMore)
                item(key = "empty") {
                    Text(
                        text = "This playlist has no songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            if (hasMore)
                item(key = "more") {
                    LaunchedEffect(songs.size) { onLoadMore() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) { CircularProgressIndicator() }
                }
        }
    }
}

@ScreenPreviews
@Composable
private fun YouTubePlaylistDetailPreview() = PreviewTheme {
    YouTubePlaylistDetailContent(
        state = LiveState.Ready(
            YouTubePlaylistDetail(
                name = "Road trip",
                thumbnail = null,
                subtitle = "Neon Coast · 42 songs",
            )
        ),
        songs = previewSongs(Origin.YOUTUBE),
        hasMore = true,
        onLoadMore = {},
        onRetry = {},
        onBack = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
    )
}
