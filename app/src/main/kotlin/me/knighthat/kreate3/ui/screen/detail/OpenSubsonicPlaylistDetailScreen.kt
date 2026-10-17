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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * A playlist of the OpenSubsonic server, fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicPlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSubsonicPlaylistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    OpenSubsonicPlaylistDetailContent(
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
 * @param share gives a link to the playlist, `null` while the user may not share
 */
@Composable
private fun OpenSubsonicPlaylistDetailContent(
    state: LiveState<OpenSubsonicPlaylistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val playlist = (state as? LiveState.Ready)?.value

    LiveDetailScreen(
        title = playlist?.name.orEmpty(),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        snackbar = snackbar,
        actions = {
            if (share != null && playlist != null)
                ShareAction(playlist.name, "playlist") { share(playlist.name) }
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
                    subtitle = null,
                    shape = MaterialTheme.shapes.medium,
                )
            }
            item(key = "queue-actions") { QueueActionRow(onAction) }
            items(detail.songs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
            if (detail.songs.isEmpty())
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
        }
    }
}

@ScreenPreviews
@Composable
private fun OpenSubsonicPlaylistDetailPreview() = PreviewTheme {
    OpenSubsonicPlaylistDetailContent(
        state = LiveState.Ready(
            OpenSubsonicPlaylistDetail(
                name = "Road trip",
                thumbnail = null,
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
