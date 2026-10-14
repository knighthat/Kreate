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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.ArtistItem
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.PlaylistItem
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel

private const val ALBUM_ITEM_SIZE = 120
private const val ARTIST_ITEM_SIZE = 100
private const val PLAYLIST_ITEM_SIZE = 220

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
 * library's lists narrowed to the matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val songs = viewModel.songs.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val playlists = viewModel.playlists.collectAsLazyPagingItems()
    val current = when (selected) {
        Tab.SONGS -> songs
        Tab.ALBUMS -> albums
        Tab.ARTISTS -> artists
        Tab.PLAYLISTS -> playlists
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(viewModel.query) },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceDim,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceDim,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = it == selected,
                        onClick = { selected = it },
                        icon = { Icon(painterResource(it.icon), contentDescription = null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        if (viewModel.origin != Origin.LOCAL || (current.itemCount == 0 &&
                    current.loadState.refresh !is LoadState.Loading)
        ) {
            Text(
                text = if (viewModel.origin != Origin.LOCAL) "Searching ${viewModel.origin.name} isn't available yet"
                else "No ${selected.label.lowercase()} found for \"${viewModel.query}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
            )
            return@Scaffold
        }

        when (selected) {
            Tab.SONGS -> LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                items(songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                    songs[index]?.let { SongItem(it, onClick = {}) }
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
