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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
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

/**
 * Every song of an artist of the OpenSubsonic server, album after album, behind the top songs of
 * their screen. Fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicArtistSongsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicArtistSongsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val songs: suspend () -> List<MediaItem> =
        { (viewModel.state.value as? LiveState.Ready)?.value.orEmpty() }

    OpenSubsonicArtistSongsContent(
        name = viewModel.name,
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        onAction = rememberQueueActions(songs),
        onSongClick = rememberSongTap(null, songs),
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun OpenSubsonicArtistSongsContent(
    name: String?,
    state: LiveState<List<MediaItem>>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LiveDetailScreen(
        title = name?.let { stringResource(R.string.screen_title_songs_by, it) }
            ?: stringResource(R.string.screen_title_songs),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        contentPadding = contentPadding,
    ) { songs, innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (songs.isNotEmpty())
                item(key = "queue-actions") { QueueActionRow(onAction) }
            items(songs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun OpenSubsonicArtistSongsPreview() = PreviewTheme {
    OpenSubsonicArtistSongsContent(
        name = "Neon Coast",
        state = LiveState.Ready(previewSongs(Origin.OPEN_SUBSONIC)),
        onRetry = {},
        onBack = {},
        onAction = {},
        onSongClick = {},
    )
}
