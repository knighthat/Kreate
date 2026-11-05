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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.theme.CoverTheme
import org.koin.androidx.compose.koinViewModel

/**
 * An album of the OpenSubsonic server, fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicAlbumDetailScreen(
    onBack: () -> Unit,
    onOpenArtist: (Route) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicAlbumDetailViewModel = koinViewModel(),
) {
    val action by Preferences.ALBUM_PAGE_ACTION.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.bookmark.isOn.collectAsStateWithLifecycle()

    val cover = (state as? LiveState.Ready)?.value?.thumbnail ?: viewModel.opened.picture
    val coverModels = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }

    CoverTheme(coverModels) {
        OpenSubsonicAlbumDetailContent(
            albumId = viewModel.albumId,
            opened = viewModel.opened,
            state = state,
            onRetry = viewModel::load,
            onBack = onBack,
            action = action,
            onAction = rememberQueueActions {
                (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
            },
            onSongClick = rememberSongTap(null) {
                (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
            },
            share = if (canShare) viewModel::shareLink else null,
            isBookmarked = isBookmarked,
            onToggleBookmark = viewModel.bookmark::toggle,
            onOpenArtist = onOpenArtist,
            onOpenStatistics = onOpenStatistics,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param share gives a link to the album, `null` while the user may not share
 * @param isBookmarked whether the album is starred on the server, `null` turns Bookmark off in the menu
 */
@Composable
private fun OpenSubsonicAlbumDetailContent(
    albumId: String,
    opened: OpenedItem,
    state: LiveState<OpenSubsonicAlbumDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    action: QueueAction,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
    isBookmarked: Boolean?,
    onToggleBookmark: () -> Unit,
    onOpenArtist: (Route) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val album = (state as? LiveState.Ready)?.value
    val resources = LocalResources.current
    val models =
        remember(album?.thumbnail) { listOfNotNull(thumbnailModel(album?.thumbnail, null)) }
    val artist = remember(album) { album?.songs?.albumArtistRoute(album.artists) }

    AlbumDetailScaffold(
        name = (album?.name ?: opened.name).orEmpty(),
        albumId = albumId,
        models = models,
        standIn = opened.picture,
        artists = album?.artists?.joinToString(", "),
        year = album?.year,
        summary = album?.songs?.takeIf { it.isNotEmpty() }?.let { songs ->
            resources.albumSummary(songs.size, songs.sumOf { it.mediaMetadata.durationMs ?: 0 })
        },
        origin = Origin.OPEN_SUBSONIC,
        isExplicit = album?.isExplicit == true,
        releaseType = album?.releaseType,
        description = album?.description,
        onBack = onBack,
        onOpenArtist = artist?.let { { onOpenArtist(it) } },
        modifier = modifier,
        contentPadding = contentPadding,
        action = action,
        onAction = onAction.takeIf { album?.songs?.isNotEmpty() == true },
        menu = { onDismiss ->
            AlbumMenu(
                name = album?.name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { album?.songs?.isNotEmpty() == true },
                isBookmarked = isBookmarked.takeIf { album != null },
                onToggleBookmark = onToggleBookmark,
                share = if (share != null && album != null) ({ share(album.name) }) else null,
                onOpenArtist = artist?.let { { onOpenArtist(it) } },
                onOpenStatistics = onOpenStatistics,
            )
        },
    ) {
        if (album == null) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@AlbumDetailScaffold
        }
        itemsIndexed(album.songs, key = { _, it -> it.mediaId }) { index, song ->
            AlbumTrack(song, index + 1, onClick = { onSongClick(song) })
        }
    }
}

@ScreenPreviews
@Composable
private fun OpenSubsonicAlbumDetailPreview() = PreviewTheme {
    OpenSubsonicAlbumDetailContent(
        albumId = "",
        opened = OpenedItem("Afterglow", null),
        state = LiveState.Ready(
            OpenSubsonicAlbumDetail(
                name = "Afterglow",
                thumbnail = null,
                artists = listOf("Neon Coast"),
                year = 2021,
                songs = previewSongs(Origin.OPEN_SUBSONIC),
                description = "Afterglow is the second album of Neon Coast, recorded over one " +
                        "summer by the sea. Its songs move from bright synths to quiet guitar " +
                        "pieces, and end where the first album started.",
            )
        ),
        onRetry = {},
        onBack = {},
        action = QueueAction.PLAY,
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isBookmarked = false,
        onToggleBookmark = {},
        onOpenArtist = {},
        onOpenStatistics = {},
    )
}
