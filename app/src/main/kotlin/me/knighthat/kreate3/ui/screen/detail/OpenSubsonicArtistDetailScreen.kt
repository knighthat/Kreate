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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.FollowButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.QueueActionRow
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel

/**
 * An artist of the OpenSubsonic server, fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicArtistDetailScreen(
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OpenSubsonicArtistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val isFollowed by viewModel.follow.isOn.collectAsStateWithLifecycle()

    OpenSubsonicArtistDetailContent(
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        onOpenAlbum = onOpenAlbum,
        onAction = rememberQueueActions {
            (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
        },
        onSongClick = rememberSongTap(null) {
            (viewModel.state.value as? LiveState.Ready)?.value?.songs.orEmpty()
        },
        share = if (canShare) viewModel::shareLink else null,
        isFollowed = isFollowed,
        onToggleFollow = viewModel.follow::toggle,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param share gives a link to the artist, `null` while the user may not share
 * @param isFollowed whether the artist is starred on the server, `null` hides the button
 */
@Composable
private fun OpenSubsonicArtistDetailContent(
    state: LiveState<OpenSubsonicArtistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: (suspend (name: String?) -> Result<String>)?,
    isFollowed: Boolean?,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val artist = (state as? LiveState.Ready)?.value

    LiveDetailScreen(
        title = artist?.name.orEmpty(),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            if (artist != null) isFollowed?.let { FollowButton(it, onToggleFollow) }
            if (share != null && artist != null)
                ShareAction(artist.name, "artist") { share(artist.name) }
        },
    ) { detail, innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                DetailHeader(
                    uri = detail.thumbnail,
                    subtitle = null,
                    shape = CircleShape,
                )
            }
            if (detail.songs.isNotEmpty())
                item(key = "queue-actions") { QueueActionRow(onAction) }
            detail.description?.let {
                item(key = "description") { DetailDescription(it) }
            }
            if (detail.albums.isNotEmpty())
                item(key = "albums") {
                    SectionTitle("Albums")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(detail.albums, key = { it.mediaId }) {
                            AlbumItem(
                                it,
                                onClick = { onOpenAlbum(it) },
                                modifier = Modifier.width(104.dp),
                            )
                        }
                    }
                }
            if (detail.songs.isNotEmpty()) {
                item(key = "songs-title") { SectionTitle("Songs") }
                items(detail.songs, key = { it.mediaId }) {
                    SongItem(it, onClick = { onSongClick(it) })
                }
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
                songs = previewSongs(Origin.OPEN_SUBSONIC),
            )
        ),
        onRetry = {},
        onBack = {},
        onOpenAlbum = {},
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isFollowed = true,
        onToggleFollow = {},
    )
}
