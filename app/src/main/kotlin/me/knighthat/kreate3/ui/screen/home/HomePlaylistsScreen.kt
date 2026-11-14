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

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playlist.ImportStep
import me.knighthat.kreate3.playlist.ImportingPlaylist
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.source.youtube.searchTime
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.ConfigToggle
import me.knighthat.kreate3.ui.component.ImportingPlaylistItem
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PlaylistItem
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.previewPlaylists
import me.knighthat.kreate3.ui.component.rememberQueueActions
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


private const val PLAYLIST_ITEM_SIZE = 220

/** Types a playlist file may have: CSV, which some file managers call plain text. */
private val PLAYLIST_FILE_TYPES =
    arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")

/**
 * Every playlist of the active user, as many to a row as fit.
 */
@Composable
fun HomePlaylistsScreen(
    onOpen: (MediaItem) -> Unit,
    onCreate: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomePlaylistsViewModel = koinViewModel(),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val pulling by viewModel.pulling.collectAsStateWithLifecycle()
    val choices by viewModel.filter.choices.collectAsStateWithLifecycle()
    val selected by viewModel.filter.selected.collectAsStateWithLifecycle()
    val offersMonthly by viewModel.offersMonthly.collectAsStateWithLifecycle()
    val monthlyOnly by viewModel.monthlyOnly.collectAsStateWithLifecycle()
    val playRandom = rememberQueueActions(viewModel::randomSongs)
    val notifier = LocalNotifier.current
    val nothingToPick = stringResource(R.string.snackbar_nothing_to_pick_playlists)
    val scope = rememberCoroutineScope()
    val importFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(viewModel::importFile)
    }
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val searchesPerSecond by Preferences.YOUTUBE_SEARCHES_PER_SECOND.state
        .collectAsStateWithLifecycle()
    pendingImport?.let {
        LookUpSongsDialog(
            songs = it.youTubeSongs,
            searchTime = searchTime(it.youTubeSongs, searchesPerSecond),
            onAnswer = viewModel::answerLookUp,
        )
    }

    // Each visit to the tab, the items of the external sources are fetched again
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomePlaylistsContent(
        playlists = viewModel.playlists.collectAsLazyPagingItems(),
        importing = importing,
        live = live,
        choices = choices,
        selected = selected,
        monthlyOnly = monthlyOnly,
        offersMonthly = offersMonthly,
        onToggleOrigin = viewModel.filter::toggle,
        onToggleMonthlyOnly = viewModel::toggleMonthlyOnly,
        onOpen = onOpen,
        onRandom = {
            scope.launch {
                val picked = viewModel.random()
                if (picked != null)
                    onOpen(picked)
                else
                    notifier.post(nothingToPick)
            }
        },
        onPlayRandom = { playRandom(QueueAction.PLAY) },
        onCreate = onCreate,
        onImport = { importFrom.launch(PLAYLIST_FILE_TYPES) },
        onSearch = onSearch,
        onProfile = onProfile,
        onSettings = onSettings,
        refreshing = pulling,
        onRefresh = viewModel::pull,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * Asks whether to look the [songs] YouTube songs of a playlist file up again before importing.
 * [onAnswer] gets `true` to look them up, `false` to import them as they are, `null` to drop the
 * import (Cancel, or the dialog dismissed).
 *
 * @param searchTime how long the lookup takes at most: when YouTube no longer has any of [songs],
 * each is searched for, paced by `BatchSearches`
 */
@Composable
private fun LookUpSongsDialog(
    songs: Int,
    searchTime: Duration,
    onAnswer: (Boolean?) -> Unit,
) = AlertDialog(
    onDismissRequest = { onAnswer(null) },
    title = { Text(stringResource(R.string.dialog_title_look_up_songs)) },
    text = {
        val resources = LocalResources.current
        Text(
            stringResource(
                R.string.dialog_body_look_up_songs,
                pluralStringResource(R.plurals.song, songs, songs),
                stringResource(R.string.source_ytm),
            ) + "\n\n" + stringResource(
                R.string.dialog_body_look_up_songs_1,
                resources.roughLength(searchTime),
            )
        )
    },
    // One under the other, like the old app's downloads dialog
    confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { onAnswer(true) }) {
                Text(stringResource(R.string.action_look_up))
            }
            TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.action_skip)) }
            TextButton(onClick = { onAnswer(null) }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    },
)

/**
 * `30 seconds`, `4 minutes`, `1 hour, 5 minutes`: [length] in the locale's own words, rounded up
 * to whole seconds under a minute and to whole minutes from there.
 */
private fun Resources.roughLength(length: Duration): String {
    val seconds = length.inWholeSeconds + if (length.inWholeMilliseconds % 1000 > 0) 1 else 0
    val shown = if (seconds < 60)
        listOf(Measure(seconds.coerceAtLeast(1), MeasureUnit.SECOND))
    else
        ((seconds + 59) / 60).minutes.toComponents { hours, minutes, _, _ ->
            listOf(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
                .filter { it.number.toLong() > 0 }
        }
    return MeasureFormat.getInstance(configuration.locales[0], MeasureFormat.FormatWidth.WIDE)
        .formatMeasures(*shown.toTypedArray())
}

/**
 * @param importing the playlists of files not made yet, above all the others
 * @param live the playlists of the external sources, after the ones of the database
 * @param monthlyOnly whether only the monthly playlists show
 * @param offersMonthly whether [monthlyOnly] can be changed: the app's own playlists show
 * @param onImport asks for a playlist file to make local playlists of
 */
@Composable
private fun HomePlaylistsContent(
    playlists: LazyPagingItems<MediaItem>,
    importing: List<ImportingPlaylist>,
    live: List<MediaItem>,
    choices: List<Origin>,
    selected: Set<Origin>,
    monthlyOnly: Boolean,
    offersMonthly: Boolean,
    onToggleOrigin: (Origin) -> Unit,
    onToggleMonthlyOnly: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRandom: () -> Unit,
    onPlayRandom: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    HomeScaffold(
        title = stringResource(R.string.screen_title_playlists),
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
            val any = playlists.itemCount > 0 || live.isNotEmpty()
            ConfigSheet(
                onDismiss = onDismiss,
                actions = { close ->
                    LabeledIconButton(
                        R.drawable.casino,
                        stringResource(R.string.menu_shortcut_open_random),
                        enabled = any,
                    ) { close(onRandom) }
                    LabeledIconButton(
                        R.drawable.play_arrow,
                        stringResource(R.string.menu_shortcut_play_random),
                        enabled = any,
                    ) { close(onPlayRandom) }
                    LabeledIconButton(
                        R.drawable.add,
                        stringResource(R.string.menu_shortcut_create_playlist),
                    ) { close(onCreate) }
                },
                entries = { close ->
                    ConfigEntry(
                        R.drawable.upload,
                        stringResource(R.string.menu_import_from_file),
                    ) { close(onImport) }
                },
                options = {
                    ConfigToggle(
                        R.drawable.date_range,
                        stringResource(R.string.menu_monthly_only),
                        monthlyOnly,
                        enabled = offersMonthly,
                        onToggle = onToggleMonthlyOnly,
                    )
                },
            )
        },
    ) { padding ->
        val gridState = rememberLazyGridState()
        // The grid keeps its first item in view when items come in above it: scroll up when a
        // placeholder is made so it shows. Keys only grow, a larger one is a new placeholder;
        // one leaving doesn't move the grid, nor do those already there when the tab shows
        val newest = importing.maxOfOrNull { it.key } ?: -1L
        var newestImport by remember { mutableLongStateOf(newest) }
        LaunchedEffect(newest) {
            if (newest > newestImport) {
                newestImport = newest
                gridState.animateScrollToItem(0)
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = PLAYLIST_ITEM_SIZE.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            items(importing, key = { "importing:${it.key}" }) {
                ImportingPlaylistItem(it.name, it.step)
            }
            items(count = playlists.itemCount, key = playlists.itemKey { it.mediaId }) { index ->
                playlists[index]?.let { PlaylistItem(it, onClick = { onOpen(it) }) }
            }
            items(live, key = { it.liveKey }) {
                PlaylistItem(it, onClick = { onOpen(it) })
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun HomePlaylistsPreview() = PreviewTheme {
    HomePlaylistsContent(
        playlists = flowOf(PagingData.from(previewPlaylists())).collectAsLazyPagingItems(),
        importing = listOf(ImportingPlaylist(0, "Imported mix", ImportStep.LOOKING_UP)),
        live = previewPlaylists(Origin.YOUTUBE).take(3),
        choices = Origin.entries,
        selected = emptySet(),
        monthlyOnly = false,
        offersMonthly = true,
        onToggleOrigin = {},
        onToggleMonthlyOnly = {},
        onOpen = {},
        onRandom = {},
        onPlayRandom = {},
        onCreate = {},
        onImport = {},
        onSearch = {},
        onProfile = {},
        onSettings = {},
        refreshing = false,
        onRefresh = {},
    )
}
