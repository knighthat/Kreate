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

package me.knighthat.kreate3.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.ArtistItem
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.MessageHost
import me.knighthat.kreate3.ui.component.PlaybackMiniPlayer
import me.knighthat.kreate3.ui.component.PlaylistItem
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.TabBar
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.rememberMiniPlayerSpace
import me.knighthat.kreate3.ui.component.rememberSongTap
import me.knighthat.kreate3.ui.component.sourceRes
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

private const val ALBUM_ITEM_SIZE = 120
private const val ARTIST_ITEM_SIZE = 100
private const val PLAYLIST_ITEM_SIZE = 220

@Composable
private fun Notice(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Items of one kind as a grid, as many to a row as fit.
 */
@Composable
private fun ResultsGrid(
    items: LazyPagingItems<MediaItem>,
    minSize: Int,
    padding: PaddingValues,
    item: @Composable (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize.dp),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        items(count = items.itemCount, key = items.itemKey { it.mediaId }) { index ->
            items[index]?.let { item(it) }
        }
    }
}

/**
 * What a search found, one tab for each kind of item like the library's. A local search shows the
 * library's lists narrowed to the matches, a YouTube search shows what YouTube finds, page after
 * page as the list scrolls.
 */
@Composable
fun SearchResultsScreen(
    initialTab: Tab,
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenArtist: (MediaItem) -> Unit,
    onOpenPlaylist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchResultsViewModel = koinViewModel(),
) {
    var selected by rememberSaveable { mutableStateOf(initialTab) }

    val songs = viewModel.songs.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val playlists = viewModel.playlists.collectAsLazyPagingItems()
    // Only the songs loaded so far are below the one tapped
    val onSongClick = rememberSongTap(Preferences.SEARCH_SONGS_TAP_RADIO) {
        songs.itemSnapshotList.items
    }

    SearchResultsContent(
        query = viewModel.query,
        origin = viewModel.origin,
        selected = selected,
        onSelect = { selected = it },
        songs = songs,
        albums = albums,
        artists = artists,
        playlists = playlists,
        onSongClick = onSongClick,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenPlaylist = onOpenPlaylist,
        onBack = onBack,
        modifier = modifier,
        miniPlayer = { PlaybackMiniPlayer(rememberMiniPlayerSpace()) },
    )
}

/**
 * @param origin where [query] was searched
 * @param miniPlayer the mini player above the tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultsContent(
    query: String,
    origin: Origin,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    songs: LazyPagingItems<MediaItem>,
    albums: LazyPagingItems<MediaItem>,
    artists: LazyPagingItems<MediaItem>,
    playlists: LazyPagingItems<MediaItem>,
    onSongClick: (MediaItem) -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenArtist: (MediaItem) -> Unit,
    onOpenPlaylist: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit,
) {
    val current = when (selected) {
        Tab.SONGS -> songs
        Tab.ALBUMS -> albums
        Tab.ARTISTS -> artists
        Tab.PLAYLISTS -> playlists
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceDim,
                ),
            )
        },
        bottomBar = {
            // The mini player sits on the tabs, like on the home screens. The lists run under
            // the whole bar already, so its room needs no splitting off
            Column {
                miniPlayer()
                TabBar(selected = selected, onSelect = onSelect)
            }
        },
        // Above the tabs, the app's scaffold would put messages over them
        snackbarHost = { MessageHost() },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        modifier = modifier,
    ) { padding ->
        val refresh = current.loadState.refresh
        if (current.itemCount == 0) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
            ) {
                when {
                    refresh is LoadState.Loading -> CircularProgressIndicator()
                    refresh is LoadState.Error -> {
                        Notice(
                            stringResource(
                                R.string.error_cant_search,
                                stringResource(origin.sourceRes)
                            )
                        )
                        Button(onClick = current::retry) { Text(stringResource(R.string.action_try_again)) }
                    }

                    else -> Notice(
                        stringResource(
                            R.string.screen_search_results_status_none,
                            pluralStringResource(selected.noun, 2),
                            stringResource(R.string.quoted, query),
                        )
                    )
                }
            }
            return@Scaffold
        }

        when (selected) {
            Tab.SONGS -> LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                items(songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                    songs[index]?.let { SongItem(it, onClick = { onSongClick(it) }) }
                }
            }

            Tab.ALBUMS -> ResultsGrid(albums, ALBUM_ITEM_SIZE, padding) {
                AlbumItem(it, onClick = { onOpenAlbum(it) })
            }

            Tab.ARTISTS -> ResultsGrid(artists, ARTIST_ITEM_SIZE, padding) {
                ArtistItem(it, onClick = { onOpenArtist(it) })
            }

            Tab.PLAYLISTS -> ResultsGrid(playlists, PLAYLIST_ITEM_SIZE, padding) {
                PlaylistItem(it, onClick = { onOpenPlaylist(it) })
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun SearchResultsPreview() = PreviewTheme {
    val albums = flowOf(PagingData.from(previewAlbums(Origin.YOUTUBE))).collectAsLazyPagingItems()
    val none = flowOf(PagingData.empty<MediaItem>()).collectAsLazyPagingItems()
    SearchResultsContent(
        query = "Neon Coast",
        origin = Origin.YOUTUBE,
        selected = Tab.ALBUMS,
        onSelect = {},
        songs = none,
        albums = albums,
        artists = none,
        playlists = none,
        onSongClick = {},
        onOpenAlbum = {},
        onOpenArtist = {},
        onOpenPlaylist = {},
        onBack = {},
        miniPlayer = {},
    )
}
