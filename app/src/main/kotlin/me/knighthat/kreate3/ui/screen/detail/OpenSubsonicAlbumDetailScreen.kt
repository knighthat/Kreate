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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.SongItem
import org.koin.androidx.compose.koinViewModel

/**
 * An album of the OpenSubsonic server, fetched when the screen opens and never stored.
 */
@Composable
fun OpenSubsonicAlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSubsonicAlbumDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canShare by viewModel.canShare.collectAsStateWithLifecycle()
    val album = (state as? LiveState.Ready)?.value

    LiveDetailScreen(
        title = album?.name.orEmpty(),
        onBack = onBack,
        viewModel = viewModel,
        modifier = modifier,
        actions = {
            if (canShare && album != null)
                ShareAction(album.name, "album") { viewModel.shareLink(album.name) }
        },
    ) { detail, innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                DetailHeader(
                    uri = detail.thumbnail,
                    title = detail.name,
                    subtitle = detail.subtitle,
                    shape = MaterialTheme.shapes.medium,
                )
            }
            items(detail.songs, key = { it.mediaId }) {
                SongItem(it, onClick = {}, showThumbnail = false, showAlbum = false)
            }
        }
    }
}
