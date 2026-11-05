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

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.ui.component.CardRowPlaceholder
import me.knighthat.kreate3.ui.component.ItemRowPlaceholder
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.TextPlaceholder
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewArtists
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.navigation.Tab
import me.knighthat.kreate3.ui.theme.CoverTheme
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
    val action by Preferences.ARTIST_PAGE_ACTION.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFollowed by viewModel.follow.isOn.collectAsStateWithLifecycle()

    val cover = viewModel.picture ?: (state as? LiveState.Ready)?.value?.thumbnail
    val coverModels = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }

    viewModel.RefreshOnPull {
        CoverTheme(coverModels) {
            YouTubeArtistDetailContent(
                artistId = viewModel.artistId,
                opened = viewModel.opened,
                picture = viewModel.picture,
                state = state,
                onRetry = viewModel::load,
                onBack = onBack,
                onOpen = onOpen,
                onSeeMore = onSeeMore,
                onOpenPlaylist = onOpenPlaylist,
                action = action,
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
    }
}

/**
 * Until the page is there, placeholders stand in for its parts under what [opened] shows.
 *
 * @param picture shown at the top instead of the page's, see [YouTubeArtistDetailViewModel.picture]
 * @param isFollowed whether the user is subscribed to the artist on YouTube, or follows them on
 * the device while signed out, `null` turns Follow off
 */
@Composable
private fun YouTubeArtistDetailContent(
    artistId: String,
    opened: OpenedItem,
    picture: Uri?,
    state: LiveState<YouTubeArtistDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    action: QueueAction,
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
    val name = artist?.name ?: opened.name

    ArtistDetailScaffold(
        name = name.orEmpty(),
        artistId = artistId,
        thumbnail = picture ?: artist?.thumbnail,
        standIn = opened.picture,
        origin = Origin.YOUTUBE,
        subtitle = artist?.audience,
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        action = action,
        onAction = onAction.takeIf { artist?.topSongs?.isNotEmpty() == true },
        menu = { onDismiss ->
            ArtistMenu(
                name = name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { artist?.topSongs?.isNotEmpty() == true },
                isFollowed = isFollowed.takeIf { artist != null },
                onToggleFollow = onToggleFollow,
                share = share,
                onOpenDetails = name?.let { { onOpenDetails(it) } },
                onOpenStatistics = onOpenStatistics,
            )
        },
    ) {
        if (state is LiveState.Failed) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@ArtistDetailScaffold
        }
        if (artist == null) {
            item(key = "description") { TextPlaceholder() }
            item(key = "songs-title") {
                SectionTitle(stringResource(R.string.section_header_top_songs))
            }
            items(SONG_PLACEHOLDERS, key = { "song-placeholder:$it" }) { ItemRowPlaceholder() }
            item(key = "section-placeholder") {
                CardRowPlaceholder(LIVE_CARD_WIDTH, Modifier.padding(top = 16.dp))
            }
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
        artistId = "",
        opened = OpenedItem("Neon Coast", null),
        picture = null,
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
        action = QueueAction.PLAY,
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isFollowed = false,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}

/** The artist's page opened from a card, while YouTube hasn't answered yet. */
@ScreenPreviews
@Composable
private fun YouTubeArtistDetailLoadingPreview() = PreviewTheme {
    YouTubeArtistDetailContent(
        artistId = "",
        opened = OpenedItem("Neon Coast", null),
        picture = null,
        state = LiveState.Loading,
        onRetry = {},
        onBack = {},
        onOpen = { _, _ -> },
        onSeeMore = { _, _ -> },
        onOpenPlaylist = {},
        action = QueueAction.PLAY,
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isFollowed = null,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}
