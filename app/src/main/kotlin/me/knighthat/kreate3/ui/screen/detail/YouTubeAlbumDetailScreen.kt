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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

/**
 * An album of YouTube, fetched when the screen opens and never stored.
 *
 * @param onSeeMore opens every item of a row that shows only some
 * @param onOpen opens an item of a row below the songs, like another version of the album
 */
@Composable
fun YouTubeAlbumDetailScreen(
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeAlbumDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    YouTubeAlbumDetailContent(
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        onOpen = onOpen,
        onSeeMore = onSeeMore,
        onAction = rememberQueueActions(snackbar) {
            (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
        },
        onSongClick = rememberSongTap(snackbar, null) {
            (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
        },
        share = viewModel::shareLink,
        snackbar = snackbar,
        modifier = modifier,
    )
}

@Composable
private fun YouTubeAlbumDetailContent(
    state: LiveState<YouTubeAlbumDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: suspend () -> Result<String>,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val album = (state as? LiveState.Ready)?.value

    LiveDetailScreen(
        title = album?.name.orEmpty(),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        snackbar = snackbar,
        actions = { ShareAction(album?.name, "album", share) },
    ) { detail, innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                DetailHeader(
                    uri = detail.thumbnail,
                    subtitle = detail.subtitle,
                    shape = MaterialTheme.shapes.medium,
                )
            }
            item(key = "queue-actions") { QueueActionRow(onAction) }
            detail.description?.let {
                item(key = "description") { DetailDescription(it) }
            }
            items(detail.songs, key = { it.mediaId }) {
                SongItem(
                    it,
                    onClick = { onSongClick(it) },
                    showThumbnail = false,
                    showAlbum = false,
                )
            }
            items(detail.sections.size, key = { "section:$it" }) {
                LiveSectionRow(detail.sections[it], onOpen, onSeeMore)
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun YouTubeAlbumDetailPreview() = PreviewTheme {
    YouTubeAlbumDetailContent(
        state = LiveState.Ready(
            YouTubeAlbumDetail(
                name = "Afterglow",
                thumbnail = null,
                subtitle = "Album · Neon Coast · 2021",
                songs = previewSongs(Origin.YOUTUBE),
                description = "Afterglow is the second album of Neon Coast, recorded over one " +
                        "summer by the sea. Its songs move from bright synths to quiet guitar " +
                        "pieces, and end where the first album started.",
                sections = listOf(
                    LiveSection(
                        title = "Other versions",
                        items = previewAlbums(Origin.YOUTUBE).map { Tab.ALBUMS to it },
                    )
                ),
            )
        ),
        onRetry = {},
        onBack = {},
        onOpen = { _, _ -> },
        onSeeMore = { _, _ -> },
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        snackbar = remember { SnackbarHostState() },
    )
}
