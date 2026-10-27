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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.playTapped
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.minutes

/**
 * How the active user listens to one song, every play of it: the times listened through, plays,
 * time, when it was first and last played, and charts of the time listened over the days, by hour
 * and by day of the week. A tap on the song plays it.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun SongStatisticsScreen(
    onBack: () -> Unit,
    viewModel: SongStatisticsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    SongStatisticsContent(
        state = state,
        onBack = onBack,
        onPlay = { item ->
            scope.launch {
                withContext(Dispatchers.Default) {
                    playback.playTapped(resources, item, listOf(item), radio = false)
                }?.let(notifier::post)
            }
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/** @param state `null` while loading */
@Composable
private fun SongStatisticsContent(
    state: SongStatisticsState?,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) = DetailScaffold(
    title = state?.item?.mediaMetadata?.title?.toString()
        ?: stringResource(R.string.screen_title_statistics),
    onBack = onBack,
    modifier = modifier,
    contentPadding = contentPadding,
) { padding ->
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        if (state == null) return@LazyColumn

        val item = state.item
        if (item == null || state.plays == 0) {
            nothingPlayed()
            return@LazyColumn
        }

        item(key = "song") { SongItem(item, onClick = { onPlay(item) }) }
        item(key = "numbers") { SongNumbers(state) }
        listeningCharts(state.charts)
    }
}

/** The numbers of the song, a label and its value on each line. */
@Composable
private fun SongNumbers(state: SongStatisticsState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    NumberLines(
        listOf(
            stringResource(R.string.statistics_song_listens) to "${state.listens}",
            stringResource(R.string.statistics_song_plays) to "${state.plays}",
            stringResource(R.string.statistics_song_time_listened) to resources.timeSpent(state.timeSpent),
            stringResource(R.string.statistics_song_first_discovered) to context.playDate(state.firstPlayed),
            stringResource(R.string.statistics_song_last_played) to context.playDate(state.lastPlayed),
        )
    )
}

@ScreenPreviews
@Composable
private fun SongStatisticsPreview() = PreviewTheme {
    SongStatisticsContent(
        state = SongStatisticsState(
            item = previewSongs().first(),
            listens = 42,
            plays = 51,
            timeSpent = 150.minutes,
            firstPlayed = 1_767_225_600_000,
            lastPlayed = 1_792_800_000_000,
            charts = previewListeningCharts(),
        ),
        onBack = {},
        onPlay = {},
    )
}
