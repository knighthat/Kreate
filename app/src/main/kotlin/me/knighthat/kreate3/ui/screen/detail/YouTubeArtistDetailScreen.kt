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
import androidx.compose.foundation.shape.CircleShape
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
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewArtists
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

/**
 * An artist of YouTube, fetched when the screen opens and never stored.
 *
 * @param onSeeMore opens every item of a row that shows only some
 * @param onOpen opens an item of a row, like an album or a related artist
 * @param onOpenPlaylist opens a YouTube playlist by its id, used for every song of the artist
 */
@Composable
fun YouTubeArtistDetailScreen(
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeArtistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    YouTubeArtistDetailContent(
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        onOpen = onOpen,
        onSeeMore = onSeeMore,
        onOpenPlaylist = onOpenPlaylist,
        onAction = rememberQueueActions(snackbar) {
            (viewModel.state.value as? LiveState.Ready)?.value?.topSongs.orEmpty()
        },
        share = viewModel::shareLink,
        snackbar = snackbar,
        modifier = modifier,
    )
}

@Composable
private fun YouTubeArtistDetailContent(
    state: LiveState<YouTubeArtistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAction: (QueueAction) -> Unit,
    share: suspend () -> Result<String>,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val artist = (state as? LiveState.Ready)?.value

    LiveDetailScreen(
        title = artist?.name.orEmpty(),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        snackbar = snackbar,
        actions = { ShareAction(artist?.name, "artist", share) },
    ) { detail, innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                DetailHeader(
                    uri = detail.thumbnail,
                    subtitle = null,
                    shape = CircleShape,
                )
            }
            if (detail.topSongs.isNotEmpty()) {
                item(key = "queue-actions") { QueueActionRow(onAction) }
                item(key = "songs-title") {
                    SectionTitle(
                        "Top songs",
                        onSeeMore = detail.songsPlaylistId?.let { id -> { onOpenPlaylist(id) } },
                    )
                }
                items(detail.topSongs, key = { it.mediaId }) { SongItem(it, onClick = {}) }
            }
            items(detail.sections.size, key = { "section:$it" }) {
                LiveSectionRow(detail.sections[it], onOpen, onSeeMore)
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun YouTubeArtistDetailPreview() = PreviewTheme {
    YouTubeArtistDetailContent(
        state = LiveState.Ready(
            YouTubeArtistDetail(
                name = "Neon Coast",
                thumbnail = null,
                topSongs = previewSongs(Origin.YOUTUBE).take(5),
                songsPlaylistId = "songs",
                sections = listOf(
                    LiveSection(
                        title = "Albums",
                        items = previewAlbums(Origin.YOUTUBE).map { Tab.ALBUMS to it },
                        more = YouTubeBrowse("albums"),
                    ),
                    LiveSection(
                        title = "Fans might also like",
                        items = previewArtists(Origin.YOUTUBE).map { Tab.ARTISTS to it },
                    ),
                ),
            )
        ),
        onRetry = {},
        onBack = {},
        onOpen = { _, _ -> },
        onSeeMore = { _, _ -> },
        onOpenPlaylist = {},
        onAction = {},
        share = { Result.success("") },
        snackbar = remember { SnackbarHostState() },
    )
}
