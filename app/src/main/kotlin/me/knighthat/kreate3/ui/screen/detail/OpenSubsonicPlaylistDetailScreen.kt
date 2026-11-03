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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.theme.CoverTheme
import org.koin.androidx.compose.koinViewModel

/**
 * A playlist of the OpenSubsonic server, fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicPlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicPlaylistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()

    val cover = (state as? LiveState.Ready)?.value?.thumbnail
    val firstCovers = (state as? LiveState.Ready)?.value?.songs.orEmpty()
    val coverModels = remember(cover, firstCovers) {
        listOfNotNull(thumbnailModel(cover, null)).ifEmpty { firstCovers.songCovers().take(1) }
    }

    CoverTheme(coverModels) {
        OpenSubsonicPlaylistDetailContent(
            state = state,
            onRetry = viewModel::load,
            onBack = onBack,
            onAction = rememberQueueActions {
                (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
            },
            onSongClick = rememberSongTap(null) {
                (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
            },
            share = if (canShare) viewModel::shareLink else null,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val playlist = (state as? LiveState.Ready)?.value
    val resources = LocalResources.current
    val cover = playlist?.thumbnail
    val models = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }
    val songCovers = remember(playlist?.songs) { playlist?.songs.orEmpty().songCovers() }

    PlaylistDetailScaffold(
        name = playlist?.name.orEmpty(),
        models = models,
        songCovers = songCovers,
        owner = null,
        summary = playlist?.songs?.takeIf { it.isNotEmpty() }?.let { songs ->
            resources.albumSummary(songs.size, songs.sumOf { it.mediaMetadata.durationMs ?: 0 })
        },
        origin = Origin.OPEN_SUBSONIC,
        onBack = onBack,
        onPlay = if (playlist?.songs?.isNotEmpty() == true) ({ onAction(QueueAction.PLAY) }) else null,
        onShuffle = { onAction(QueueAction.SHUFFLE) },
        modifier = modifier,
        contentPadding = contentPadding,
        menu = { onDismiss ->
            PlaylistMenu(
                name = playlist?.name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { playlist?.songs?.isNotEmpty() == true },
                share = if (share != null && playlist != null) ({ share(playlist.name) }) else null,
            )
        },
    ) {
        if (playlist == null) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@PlaylistDetailScaffold
        }
        items(playlist.songs, key = { it.mediaId }) {
            SongItem(it, onClick = { onSongClick(it) })
        }
        if (playlist.songs.isEmpty())
            item(key = "empty") { EmptyPlaylist() }
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
    )
}
