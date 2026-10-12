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

package me.knighthat.kreate.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate.ui.component.AlbumItem
import me.knighthat.kreate.ui.component.ArtistItem
import me.knighthat.kreate.ui.component.SongItem
import me.knighthat.kreate.ui.component.toMediaItem
import me.knighthat.kreate.ui.theme.KreateTheme
import org.koin.androidx.compose.koinViewModel


private const val SONG_ROWS = 3
private val SONG_HEIGHT = 64.dp
private val SONG_WIDTH = 280.dp

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun KreateApp(
    viewModel: KreateAppViewModel = koinViewModel()
) {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()

    KreateTheme(
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        colorSeed = Color.Unspecified,
    ) {
        Scaffold(
            topBar = {},
            bottomBar = {},
            containerColor = MaterialTheme.colorScheme.surfaceDim,
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            val songs = viewModel.songs.collectAsLazyPagingItems()
            val albums = viewModel.albums.collectAsLazyPagingItems()
            val artists = viewModel.artists.collectAsLazyPagingItems()

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                Section("Songs")
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(SONG_ROWS),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SONG_HEIGHT * SONG_ROWS),
                ) {
                    items(count = songs.itemCount, key = songs.itemKey { it.songId }) { index ->
                        // A song that can't be played right now has no item, its cell stays empty
                        val item = songs[index]?.let { remember(it) { it.toMediaItem() } }
                        if (item != null)
                            SongItem(item, onClick = {}, modifier = Modifier.width(SONG_WIDTH))
                    }
                }

                Section("Albums")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = albums.itemCount, key = albums.itemKey { it.albumId }) { index ->
                        val item = albums[index]?.let { remember(it) { it.toMediaItem() } }
                        if (item != null)
                            AlbumItem(item, onClick = {}, modifier = Modifier.width(104.dp))
                    }
                }

                Section("Artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        count = artists.itemCount,
                        key = artists.itemKey { it.artistId }) { index ->
                        val item = artists[index]?.let { remember(it) { it.toMediaItem() } }
                        if (item != null)
                            ArtistItem(item, onClick = {}, modifier = Modifier.width(80.dp))
                    }
                }
            }
        }
    }
}