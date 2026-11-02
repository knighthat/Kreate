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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewArtists
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
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
    onOpenDetails: (name: String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: YouTubeArtistDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFollowed by viewModel.follow.isOn.collectAsStateWithLifecycle()

    YouTubeArtistDetailContent(
        state = state,
        onRetry = viewModel::load,
        onBack = onBack,
        onOpen = onOpen,
        onSeeMore = onSeeMore,
        onOpenPlaylist = onOpenPlaylist,
        onAction = rememberQueueActions {
            (viewModel.state.value as? LiveState.Ready)?.value?.topSongs.orEmpty()
        },
        onSongClick = rememberSongTap(null) {
            (viewModel.state.value as? LiveState.Ready)?.value?.topSongs.orEmpty()
        },
        share = viewModel::shareLink,
        isFollowed = isFollowed,
        onToggleFollow = viewModel.follow::toggle,
        onOpenDetails = onOpenDetails,
        onOpenStatistics = onOpenStatistics,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param isFollowed whether the user is subscribed to the artist on YouTube, or follows them on
 * the device while signed out, `null` turns Follow off
 */
@Composable
private fun YouTubeArtistDetailContent(
    state: LiveState<YouTubeArtistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    share: suspend () -> Result<String>,
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
        origin = Origin.YOUTUBE,
        subtitle = artist?.audience,
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
                share = share,
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
        if (artist.topSongs.isNotEmpty()) {
            item(key = "songs-title") {
                SectionTitle(
                    stringResource(R.string.section_header_top_songs),
                    onSeeMore = artist.songsPlaylistId?.let { id -> { onOpenPlaylist(id) } },
                )
            }
            items(artist.topSongs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
        }
        items(artist.sections.size, key = { "section:$it" }) {
            LiveSectionRow(artist.sections[it], onOpen, onSeeMore)
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
                audience = "12.3M monthly audience",
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
        onSongClick = {},
        share = { Result.success("") },
        isFollowed = false,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}
