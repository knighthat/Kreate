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

package me.knighthat.kreate3.ui.screen.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.viewmodel.koinViewModel

/** Colour of the mark on a picked song. */
private val PICKED = Color(0xFF2E7D32)

/**
 * The songs the old app had downloaded, to pick the ones to download. A tap picks a song or takes
 * it back, a picked song shows a check mark in place of its cover. Leaving the page downloads
 * the songs picked.
 *
 * @param onBack leaves the page, which downloads the songs picked (none is fine)
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun LegacyDownloadsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LegacyDownloadsViewModel = koinViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LegacyDownloadsContent(
        songs = songs,
        selected = selected,
        loading = loading,
        onToggle = viewModel::toggle,
        onClear = viewModel::clear,
        onSelectAll = viewModel::selectAll,
        // Leaving keeps what is picked, which may be nothing
        onBack = {
            viewModel.download()
            onBack()
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun LegacyDownloadsContent(
    songs: List<MediaItem>,
    selected: Set<String>,
    loading: LegacyLoading,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    BackHandler(onBack = onBack)

    DetailScaffold(
        title = stringResource(R.string.screen_title_old_app_downloads),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.action_clear_all))
            }
            TextButton(onClick = onSelectAll, enabled = selected.size < songs.size) {
                Text(stringResource(R.string.action_select_all))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Nothing to show yet: the loading takes the page
            if (songs.isEmpty() && loading.isLoading)
                item(key = "loading") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 96.dp),
                    ) {
                        CircularProgressIndicator()
                        LoadingText(loading)
                    }
                }

            if (loading.failed)
                item(key = "failed") {
                    Text(
                        stringResource(
                            R.string.screen_old_app_downloads_status_failed,
                            stringResource(R.string.source_ytm)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

            if (songs.isEmpty() && !loading.isLoading && !loading.failed)
                item(key = "empty") {
                    Text(
                        stringResource(R.string.screen_old_app_downloads_status_nothing_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }

            items(songs, key = { it.mediaId }) { item ->
                val picked = item.mediaId in selected
                SongItem(
                    item = item,
                    onClick = { onToggle(item.mediaId) },
                    hasMenu = false,
                    thumbnailOverlay = if (picked) {
                        { PickedMark() }
                    } else {
                        null
                    },
                )
            }

            // More songs are coming, seen once the list is scrolled to its end
            if (songs.isNotEmpty() && loading.isLoading)
                item(key = "loading more") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        LoadingText(loading)
                    }
                }
        }
    }
}

@Composable
private fun LoadingText(loading: LegacyLoading) = Text(
    stringResource(R.string.screen_old_app_downloads_status_loading, loading.found, loading.total),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** A green check mark over the cover of a picked song. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PickedMark() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(MaterialTheme.shapes.small)
            .background(PICKED),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.check),
            contentDescription = stringResource(R.string.semantic_selected),
            tint = Color.White,
        )
    }
}

/**
 * Tells how many songs the old app had downloaded and asks what to do. Only the last button
 * closes it, a tap outside or the back button doesn't.
 */
@Composable
fun LegacyDownloadsDialog(
    count: Int,
    onDownloadAll: () -> Unit,
    onSelect: () -> Unit,
    onSkip: () -> Unit,
) = AlertDialog(
    onDismissRequest = {},
    title = { Text(stringResource(R.string.dialog_title_old_app_downloads)) },
    text = { Text(pluralStringResource(R.plurals.dialog_body_old_app_downloads, count, count)) },
    // One under the other, three labels don't fit side by side
    confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onDownloadAll) { Text(stringResource(R.string.action_download_all)) }
            TextButton(onClick = onSelect) { Text(stringResource(R.string.action_select)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.action_close)) }
        }
    },
)

//<editor-fold defaultstate="collapsed" desc="Previews">
@ScreenPreviews
@Composable
private fun LegacyDownloadsPreview() = PreviewTheme {
    val songs = previewSongs(Origin.YOUTUBE)
    LegacyDownloadsContent(
        songs = songs,
        selected = setOf(songs[0].mediaId, songs[2].mediaId),
        loading = LegacyLoading(found = songs.size, done = 200, total = 340),
        onBack = {},
        onToggle = {},
        onClear = {},
        onSelectAll = {},
    )
}

@ScreenPreviews
@Composable
private fun LegacyDownloadsDialogPreview() = PreviewTheme {
    LegacyDownloadsDialog(count = 128, onDownloadAll = {}, onSelect = {}, onSkip = {})
}
//</editor-fold>
