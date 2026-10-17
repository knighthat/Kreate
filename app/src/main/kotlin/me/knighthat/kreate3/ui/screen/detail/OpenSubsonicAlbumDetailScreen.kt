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
 * An album of the OpenSubsonic server, fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicAlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSubsonicAlbumDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    OpenSubsonicAlbumDetailContent(
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        onAction = rememberQueueActions(snackbar) {
            (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
        },
        onSongClick = rememberSongTap(snackbar, null) {
            (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
        },
        share = if (canShare) viewModel::shareLink else null,
        snackbar = snackbar,
        modifier = modifier,
    )
}

/**
 * @param share gives a link to the album, `null` while the user may not share
 */
@Composable
private fun OpenSubsonicAlbumDetailContent(
    state: LiveState<OpenSubsonicAlbumDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
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
        actions = {
            if (share != null && album != null)
                ShareAction(album.name, "album") { share(album.name) }
        },
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
            items(detail.songs, key = { it.mediaId }) {
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

@ScreenPreviews
@Composable
private fun OpenSubsonicAlbumDetailPreview() = PreviewTheme {
    OpenSubsonicAlbumDetailContent(
        state = LiveState.Ready(
            OpenSubsonicAlbumDetail(
                name = "Afterglow",
                thumbnail = null,
                subtitle = "Neon Coast · 2021",
                songs = previewSongs(Origin.OPEN_SUBSONIC),
            )
        ),
        onRetry = {},
        onBack = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        snackbar = remember { SnackbarHostState() },
    )
}
