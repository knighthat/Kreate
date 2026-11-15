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

package me.knighthat.kreate3.ui.screen.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.ConfigToggle
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberQueueActions
import me.knighthat.kreate3.ui.component.rememberSongTap
import org.koin.androidx.compose.koinViewModel

/**
 * Every song in the library, one per row.
 *
 * @param onRecognize opens song recognition's dialog, from the sheet
 */
@Composable
fun HomeSongsScreen(
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onRecognize: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeSongsViewModel = koinViewModel(),
) {
    val pulling by viewModel.pulling.collectAsStateWithLifecycle()
    val choices by viewModel.filter.choices.collectAsStateWithLifecycle()
    val selected by viewModel.filter.selected.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val recognitionEnabled by Preferences.SHAZAM_ENABLED.state.collectAsStateWithLifecycle()

    // Each visit to the tab, the songs liked on YouTube are brought up to date
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeSongsContent(
        songs = viewModel.songs.collectAsLazyPagingItems(),
        choices = choices,
        selected = selected,
        filters = filters,
        onToggleOrigin = viewModel.filter::toggle,
        onFilter = viewModel::filter,
        onAction = rememberQueueActions(viewModel::allSongs),
        onSongClick = rememberSongTap(
            Preferences.HOME_SONGS_TAP_RADIO,
            viewModel::allSongs
        ),
        onSearch = onSearch,
        onProfile = onProfile,
        onSettings = onSettings,
        onRecognize = onRecognize,
        recognitionEnabled = recognitionEnabled,
        refreshing = pulling,
        onRefresh = viewModel::pull,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun HomeSongsContent(
    songs: LazyPagingItems<MediaItem>,
    choices: List<Origin>,
    selected: Set<Origin>,
    filters: SongFilters,
    onToggleOrigin: (Origin) -> Unit,
    onFilter: ((SongFilters) -> SongFilters) -> Unit,
    onAction: (QueueAction) -> Unit,
    onSongClick: (MediaItem) -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onRecognize: () -> Unit,
    recognitionEnabled: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    HomeScaffold(
        title = stringResource(R.string.screen_title_songs),
        choices = choices,
        selected = selected,
        onToggleOrigin = onToggleOrigin,
        modifier = modifier,
        contentPadding = contentPadding,
        onSearch = onSearch,
        onProfile = onProfile,
        onSettings = onSettings,
        refreshing = refreshing,
        onRefresh = onRefresh,
        sheet = { onDismiss ->
            SongsSheet(filters, onFilter, onAction, onRecognize, recognitionEnabled, onDismiss)
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = songs.itemCount,
                key = songs.itemKey { it.mediaId }
            ) { index ->
                songs[index]?.let { SongItem(it, onClick = { onSongClick(it) }) }
            }
        }
    }
}

/**
 * The quick actions on every song shown, recognizing a song, then what the tab shows. Cached only and Downloaded only
 * exclude each other, turning one on turns the other off.
 *
 * @param recognitionEnabled [Preferences.SHAZAM_ENABLED]; while off, Recognize song is grayed out
 * and says it's disabled in settings
 */
@Composable
private fun SongsSheet(
    filters: SongFilters,
    onFilter: ((SongFilters) -> SongFilters) -> Unit,
    onAction: (QueueAction) -> Unit,
    onRecognize: () -> Unit,
    recognitionEnabled: Boolean,
    onDismiss: () -> Unit,
) = ConfigSheet(
    onDismiss = onDismiss,
    actions = { close ->
        LabeledIconButton(R.drawable.play_arrow, stringResource(R.string.menu_shortcut_play_all)) {
            close { onAction(QueueAction.PLAY) }
        }
        LabeledIconButton(R.drawable.shuffle, stringResource(R.string.menu_shortcut_shuffle)) {
            close { onAction(QueueAction.SHUFFLE) }
        }
        LabeledIconButton(
            R.drawable.queue_play_next,
            stringResource(R.string.menu_shortcut_play_next),
        ) {
            close { onAction(QueueAction.PLAY_NEXT) }
        }
        LabeledIconButton(
            R.drawable.playlist_add,
            stringResource(R.string.menu_shortcut_add_to_queue),
        ) {
            close { onAction(QueueAction.ENQUEUE) }
        }
    },
    entries = { close ->
        ConfigEntry(
            R.drawable.mic,
            stringResource(R.string.menu_recognize_song),
            enabled = recognitionEnabled,
            summary = if (recognitionEnabled)
                null
            else
                stringResource(R.string.status_disabled_in_settings),
        ) { close(onRecognize) }
    },
    options = {
        ConfigToggle(
            R.drawable.thumb_down,
            stringResource(R.string.menu_show_disliked),
            filters.withDisliked,
        ) { onFilter { it.copy(withDisliked = !it.withDisliked) } }
        ConfigToggle(
            R.drawable.cloud_done,
            stringResource(R.string.menu_cached_only),
            filters.cachedOnly,
        ) { onFilter { it.copy(cachedOnly = !it.cachedOnly, downloadedOnly = false) } }
        ConfigToggle(
            R.drawable.downloaded,
            stringResource(R.string.menu_downloaded_only),
            filters.downloadedOnly,
        ) { onFilter { it.copy(downloadedOnly = !it.downloadedOnly, cachedOnly = false) } }
    },
)

@ScreenPreviews
@Composable
private fun HomeSongsPreview() = PreviewTheme {
    HomeSongsContent(
        songs = flowOf(PagingData.from(previewSongs() + previewSongs(Origin.YOUTUBE)))
            .collectAsLazyPagingItems(),
        choices = Origin.entries,
        selected = setOf(Origin.LOCAL),
        filters = SongFilters(),
        onToggleOrigin = {},
        onFilter = {},
        onAction = {},
        onSongClick = {},
        onSearch = {},
        onProfile = {},
        onSettings = {},
        onRecognize = {},
        recognitionEnabled = true,
        refreshing = false,
        onRefresh = {},
    )
}
