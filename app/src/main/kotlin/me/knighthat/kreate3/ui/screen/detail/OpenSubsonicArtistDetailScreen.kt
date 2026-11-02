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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
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
import me.knighthat.kreate3.ui.component.rememberQuickQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.theme.CoverTheme
import org.koin.androidx.compose.koinViewModel

/**
 * An artist of the OpenSubsonic server, fetched when the screen opens and never stored.
 *
 * @param onOpenSongs opens every song of the artist, named as given
 */
@Composable
fun OpenSubsonicArtistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenSongs: (name: String?) -> Unit,
    onOpenDetails: (name: String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicArtistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val isFollowed by viewModel.follow.isOn.collectAsStateWithLifecycle()

    val cover = (state as? LiveState.Ready)?.value?.thumbnail
    val coverModels = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }

    CoverTheme(coverModels) {
        OpenSubsonicArtistDetailContent(
            state = state,
            onRetry = viewModel::load,
            onBack = onBack,
            onOpenAlbum = onOpenAlbum,
            onOpenSongs = onOpenSongs,
            onAction = rememberQuickQueueActions(
                shown = { (viewModel.state.value as? LiveState.Ready)?.value?.topSongs.orEmpty() },
                songs = viewModel::allSongs,
            ),
            onSongClick = rememberSongTap(null) {
                (viewModel.state.value as? LiveState.Ready)?.value?.topSongs.orEmpty()
            },
            share = if (canShare) viewModel::shareLink else null,
            isFollowed = isFollowed,
            onToggleFollow = viewModel.follow::toggle,
            onOpenDetails = onOpenDetails,
            onOpenStatistics = onOpenStatistics,
            modifier = modifier,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param share gives a link to the artist, `null` while the user may not share
 * @param isFollowed whether the artist is starred on the server, `null` turns Follow off
 */
@Composable
private fun OpenSubsonicArtistDetailContent(
    state: LiveState<OpenSubsonicArtistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenSongs: (name: String?) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
    isFollowed: Boolean?,
    onToggleFollow: () -> Unit,
    onOpenDetails: (name: String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val artist = (state as? LiveState.Ready)?.value

    ArtistDetailScaffold(
        name = artist?.name.orEmpty(),
        thumbnail = artist?.thumbnail,
        origin = Origin.OPEN_SUBSONIC,
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        menu = { onDismiss ->
            ArtistMenu(
                name = artist?.name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { artist?.topSongs?.isNotEmpty() == true },
                isFollowed = isFollowed.takeIf { artist != null },
                onToggleFollow = onToggleFollow,
                share = if (share != null && artist != null) ({ share(artist.name) }) else null,
                onOpenDetails = artist?.name?.let { { onOpenDetails(it) } },
                onOpenStatistics = onOpenStatistics,
            )
        },
    ) {
        if (artist == null) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@ArtistDetailScaffold
        }
        artist.description?.let {
            item(key = "description") { DetailDescription(it) }
        }
        if (artist.albums.isNotEmpty())
            item(key = "albums") {
                SectionTitle(
                    pluralStringResource(
                        R.plurals.unit_album,
                        2
                    ).replaceFirstChar { it.uppercase() })
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(artist.albums, key = { it.mediaId }) {
                        AlbumItem(
                            it,
                            onClick = { onOpenAlbum(it) },
                            modifier = Modifier.width(104.dp),
                        )
                    }
                }
            }
        if (artist.topSongs.isNotEmpty()) {
            item(key = "songs-title") {
                SectionTitle(
                    stringResource(R.string.section_header_top_songs),
                    onSeeMore = if (artist.hasMoreSongs) ({ onOpenSongs(artist.name) }) else null,
                )
            }
            items(artist.topSongs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun OpenSubsonicArtistDetailPreview() = PreviewTheme {
    OpenSubsonicArtistDetailContent(
        state = LiveState.Ready(
            OpenSubsonicArtistDetail(
                name = "Neon Coast",
                thumbnail = null,
                albums = previewAlbums(Origin.OPEN_SUBSONIC).take(3),
                topSongs = previewSongs(Origin.OPEN_SUBSONIC),
                hasMoreSongs = true,
            )
        ),
        onRetry = {},
        onBack = {},
        onOpenAlbum = {},
        onOpenSongs = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isFollowed = true,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}
