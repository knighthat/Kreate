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
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.CardRowPlaceholder
import me.knighthat.kreate3.ui.component.ItemRowPlaceholder
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.TextPlaceholder
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
    val action by Preferences.ARTIST_PAGE_ACTION.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val isFollowed by viewModel.follow.isOn.collectAsStateWithLifecycle()

    val cover = (state as? LiveState.Ready)?.value?.thumbnail ?: viewModel.opened.picture
    val coverModels = remember(cover) { listOfNotNull(thumbnailModel(cover, null)) }
    val shownSongs = { (viewModel.topSongs.value as? LiveState.Ready)?.value?.songs.orEmpty() }

    CoverTheme(coverModels) {
        OpenSubsonicArtistDetailContent(
            artistId = viewModel.artistId,
            opened = viewModel.opened,
            state = state,
            topSongs = topSongs,
            description = description,
            onRetry = viewModel::load,
            onBack = onBack,
            onOpenAlbum = onOpenAlbum,
            onOpenSongs = onOpenSongs,
            action = action,
            onAction = rememberQuickQueueActions(shown = shownSongs, songs = viewModel::allSongs),
            onSongClick = rememberSongTap(null, shownSongs),
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
 * Every part shows as soon as it is there, the others stand in with placeholders until then.
 *
 * @param opened shown until [state] has the artist
 * @param share gives a link to the artist, `null` while the user may not share
 * @param isFollowed whether the artist is starred on the server, `null` turns Follow off
 */
@Composable
private fun OpenSubsonicArtistDetailContent(
    artistId: String,
    opened: OpenedItem,
    state: LiveState<OpenSubsonicArtistDetail>,
    topSongs: LiveState<TopSongs>,
    description: LiveState<String?>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenSongs: (name: String?) -> Unit,
    action: QueueAction,
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
    val name = artist?.name ?: opened.name
    val songs = (topSongs as? LiveState.Ready)?.value

    ArtistDetailScaffold(
        name = name.orEmpty(),
        artistId = artistId,
        thumbnail = artist?.thumbnail ?: opened.picture,
        standIn = opened.picture,
        origin = Origin.OPEN_SUBSONIC,
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        action = action,
        onAction = onAction.takeIf { songs?.songs?.isNotEmpty() == true },
        menu = { onDismiss ->
            ArtistMenu(
                name = name,
                onDismiss = onDismiss,
                onAction = onAction.takeIf { songs?.songs?.isNotEmpty() == true },
                isFollowed = isFollowed.takeIf { artist != null },
                onToggleFollow = onToggleFollow,
                share = if (share != null && artist != null) ({ share(artist.name) }) else null,
                onOpenDetails = name?.let { { onOpenDetails(it) } },
                onOpenStatistics = onOpenStatistics,
            )
        },
    ) {
        if (state is LiveState.Failed) {
            item(key = "status") { LiveStatus(state, onRetry, Modifier.fillMaxWidth()) }
            return@ArtistDetailScaffold
        }
        when (description) {
            is LiveState.Ready -> description.value?.let {
                item(key = "description") { DetailDescription(it) }
            }

            else -> item(key = "description") { TextPlaceholder() }
        }
        if (artist == null) {
            item(key = "albums") {
                SectionTitle(albumsTitle())
                CardRowPlaceholder(ALBUM_CARD_WIDTH)
            }
        } else if (artist.albums.isNotEmpty()) {
            item(key = "albums") {
                SectionTitle(albumsTitle())
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(artist.albums, key = { it.mediaId }) {
                        AlbumItem(
                            it,
                            onClick = { onOpenAlbum(it) },
                            modifier = Modifier.width(ALBUM_CARD_WIDTH.dp),
                        )
                    }
                }
            }
        }
        if (songs == null) {
            item(key = "songs-title") {
                SectionTitle(stringResource(R.string.section_header_top_songs))
            }
            items(SONG_PLACEHOLDERS, key = { "song-placeholder:$it" }) { ItemRowPlaceholder() }
        } else if (songs.songs.isNotEmpty()) {
            item(key = "songs-title") {
                SectionTitle(
                    stringResource(R.string.section_header_top_songs),
                    onSeeMore = if (songs.hasMore) ({ onOpenSongs(name) }) else null,
                )
            }
            items(songs.songs, key = { it.mediaId }) {
                SongItem(it, onClick = { onSongClick(it) })
            }
        }
    }
}

/** Width of an album card in the row of the artist's albums. */
private const val ALBUM_CARD_WIDTH = 104

@Composable
private fun albumsTitle(): String =
    pluralStringResource(R.plurals.unit_album, 2).replaceFirstChar { it.uppercase() }

@ScreenPreviews
@Composable
private fun OpenSubsonicArtistDetailPreview() = PreviewTheme {
    OpenSubsonicArtistDetailContent(
        artistId = "",
        opened = OpenedItem("Neon Coast", null),
        state = LiveState.Ready(
            OpenSubsonicArtistDetail(
                name = "Neon Coast",
                thumbnail = null,
                albums = previewAlbums(Origin.OPEN_SUBSONIC).take(3),
            )
        ),
        topSongs = LiveState.Ready(TopSongs(previewSongs(Origin.OPEN_SUBSONIC), hasMore = true)),
        description = LiveState.Ready(null),
        onRetry = {},
        onBack = {},
        onOpenAlbum = {},
        onOpenSongs = {},
        action = QueueAction.PLAY,
        onAction = {},
        onSongClick = {},
        share = { Result.success("") },
        isFollowed = true,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}

/** The artist's page while their top songs and biography are still on the way. */
@ScreenPreviews
@Composable
private fun OpenSubsonicArtistDetailLoadingPreview() = PreviewTheme {
    OpenSubsonicArtistDetailContent(
        artistId = "",
        opened = OpenedItem("Neon Coast", null),
        state = LiveState.Ready(
            OpenSubsonicArtistDetail(
                name = "Neon Coast",
                thumbnail = null,
                albums = previewAlbums(Origin.OPEN_SUBSONIC).take(3),
            )
        ),
        topSongs = LiveState.Loading,
        description = LiveState.Loading,
        onRetry = {},
        onBack = {},
        onOpenAlbum = {},
        onOpenSongs = {},
        action = QueueAction.PLAY,
        onAction = {},
        onSongClick = {},
        share = null,
        isFollowed = null,
        onToggleFollow = {},
        onOpenDetails = {},
        onOpenStatistics = {},
    )
}
