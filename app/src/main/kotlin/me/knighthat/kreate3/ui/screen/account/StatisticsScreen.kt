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

import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.ListeningSummary
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.ConfigButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewArtists
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.viewmodel.koinViewModel
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * How the active user listens, in the days and from the sources picked in the sheet the top bar
 * opens (all time and every source at first): totals, the songs, albums and artists
 * listened to the most (by the times listened through, not plays) and the time per source. A tap
 * on a song, an album or an artist opens its own statistics.
 *
 * @param onOpenSong opens the statistics of the song with this id
 * @param onOpenAlbum opens the statistics of the album
 * @param onOpenArtist opens the statistics of the artist
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenArtist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: StatisticsViewModel = koinViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    StatisticsContent(
        filter = filter,
        choices = choices,
        state = state,
        onBack = onBack,
        onPickRange = viewModel::pickRange,
        onToggleOrigin = viewModel::toggleOrigin,
        onOpenSong = onOpenSong,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param choices the origins the sheet offers
 * @param state `null` while loading
 */
@Composable
private fun StatisticsContent(
    filter: StatisticsFilter,
    choices: List<Origin>,
    state: StatisticsState?,
    onBack: () -> Unit,
    onPickRange: (HistoryRange?) -> Unit,
    onToggleOrigin: (Origin) -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenArtist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var options by rememberSaveable { mutableStateOf(false) }

    if (options)
        StatisticsSheet(
            choices = choices,
            filter = filter,
            onToggleOrigin = onToggleOrigin,
            onPickRange = onPickRange,
            onDismiss = { options = false },
        )

    DetailScaffold(
        title = stringResource(R.string.screen_title_statistics),
        subtitle = periodText(filter.range),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = { ConfigButton({ options = true }) },
    ) { padding ->
        StatisticsList(
            filter = filter,
            state = state,
            onOpenSong = onOpenSong,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            contentPadding = padding,
        )
    }
}

/** The numbers, tops and sources of [state], nothing while it's another [filter]'s. */
@Composable
private fun StatisticsList(
    filter: StatisticsFilter,
    state: StatisticsState?,
    onOpenSong: (String) -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenArtist: (MediaItem) -> Unit,
    contentPadding: PaddingValues,
) {
    // The state of another filter shows nothing until it's ready
    val shown = state?.takeIf { it.filter == filter }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        if (shown != null && shown.summary.plays == 0)
            item(key = "empty") {
                Text(
                    stringResource(
                        if (shown.period == StatisticsPeriod.THIS_MONTH)
                            R.string.screen_statistics_status_nothing_this_month
                        else
                            R.string.status_nothing_played
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillParentMaxSize()
                        .wrapContentSize(Alignment.Center),
                )
            }
        if (shown == null || shown.summary.plays == 0) return@LazyColumn

        item(key = "summary") { SummaryTiles(shown.summary, shown.previous) }

        if (shown.songs.isNotEmpty()) {
            item(key = "songs-title") {
                SectionTitle(stringResource(R.string.section_header_top_songs))
            }
            items(shown.songs, key = { "song:${it.item.mediaId}" }) { song ->
                SongItem(
                    item = song.item,
                    onClick = { onOpenSong(song.item.mediaId) },
                    trailing = { MinutesText(song.timeSpent) },
                )
            }
        }

        if (shown.albums.isNotEmpty())
            item(key = "albums") {
                SectionTitle(stringResource(R.string.section_header_top_albums))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    shown.albums.forEach { album ->
                        Column(Modifier.weight(1f)) {
                            AlbumItem(album.item, onClick = { onOpenAlbum(album.item) })
                            ListensText(album.listens)
                        }
                    }
                    // Fewer albums keep the size of a full row
                    repeat(ALBUMS_PER_ROW - shown.albums.size) { Spacer(Modifier.weight(1f)) }
                }
            }

        if (shown.artists.isNotEmpty())
            item(key = "artists") {
                SectionTitle(stringResource(R.string.section_header_top_artists))
                ArtistColumns(shown.artists, onOpenArtist)
            }

        if (shown.origins.isNotEmpty())
            item(key = "sources") {
                SectionTitle(stringResource(R.string.section_header_listening_by_source))
                SourcesPie(shown.origins)
            }
    }
}

/**
 * The days of [range] as the locale writes a range of dates ("Oct 1 – 31, 2026"), "All time" for
 * every day.
 */
@Composable
private fun periodText(range: HistoryRange?): String {
    if (range == null) return stringResource(R.string.constant_statistics_period_all_time)

    val context = LocalContext.current
    // Read so the text follows a change of language
    val resources = LocalResources.current
    return remember(range, resources) {
        val zone = TimeZone.getDefault()
        // The end is the midnight after the last day, which the range leaves out
        DateUtils.formatDateRange(
            context,
            range.start(zone),
            range.end(zone),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
}

/**
 * The filters of the page, in a sheet: the sources to count, then the days, by a preset or on the
 * calendar. A filter applies as soon as it's picked; a range on the calendar once its last day is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsSheet(
    choices: List<Origin>,
    filter: StatisticsFilter,
    onToggleOrigin: (Origin) -> Unit,
    onPickRange: (HistoryRange?) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val zone = remember { TimeZone.getDefault() }
    val presets = remember(now, zone) {
        StatisticsPeriod.entries.map { RangePreset(it.labelRes, it.range(now, zone)) }
    }

    // Opens halfway, on the filters; the calendar is a drag up
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OriginFilter(choices, filter.origins, onToggleOrigin)
            DateRangeFilter(filter.range, presets, onPickRange)
        }
    }
}

/** Time, plays, songs and artists, two by two, each with its change since last month when [previous] is given. */
@Composable
private fun SummaryTiles(summary: ListeningSummary, previous: ListeningSummary?) {
    val resources = LocalResources.current

    fun change(now: Long, before: Long?): String? =
        resources.change(now, before ?: return null)

    // Tiles of a row are as tall as the tallest, a long time takes two lines
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            SummaryTile(
                value = resources.timeSpent(summary.timeSpent),
                caption = stringResource(R.string.statistics_summary_title_time_listened),
                change = change(
                    summary.timeSpent.inWholeSeconds,
                    previous?.timeSpent?.inWholeSeconds
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SummaryTile(
                value = "${summary.plays}",
                caption = stringResource(R.string.statistics_summary_title_plays),
                change = change(summary.plays.toLong(), previous?.plays?.toLong()),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            SummaryTile(
                value = "${summary.songs}",
                caption = stringResource(R.string.statistics_summary_title_songs),
                change = change(summary.songs.toLong(), previous?.songs?.toLong()),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SummaryTile(
                value = "${summary.artists}",
                caption = stringResource(R.string.statistics_summary_title_artists),
                change = change(summary.artists.toLong(), previous?.artists?.toLong()),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SummaryTile(
    value: String,
    caption: String,
    change: String?,
    modifier: Modifier = Modifier,
) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = modifier,
) {
    Column(Modifier.padding(12.dp)) {
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
        if (change != null)
            Text(
                change,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

/** The whole times something was listened through. */
@Composable
private fun ListensText(listens: Double) {
    val count = listens.toInt()
    Text(
        pluralStringResource(R.plurals.listen, count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "+12% vs last month", `null` when there is nothing to compare with. */
private fun Resources.change(now: Long, before: Long): String? {
    if (before <= 0) return null
    val percent = ((now - before) * 100.0 / before).roundToInt()
    val signed = (if (percent > 0) "+" else "") + getString(R.string.percent, percent)
    return getString(R.string.statistics_change_vs_last_month, signed)
}

private const val ALBUMS_PER_ROW = 3

@ScreenPreviews
@Composable
private fun StatisticsPreview() = PreviewTheme {
    val songs = previewSongs()
    val filter = StatisticsFilter(HistoryRange(firstDay = 0, lastDay = 0))
    StatisticsContent(
        filter = filter,
        choices = listOf(Origin.LOCAL, Origin.YOUTUBE, Origin.OPEN_SUBSONIC),
        state = StatisticsState(
            filter = filter,
            period = StatisticsPeriod.THIS_MONTH,
            summary = ListeningSummary(214, 87, 31, 812.minutes),
            previous = ListeningSummary(180, 92, 31, 700.minutes),
            songs = songs.take(5)
                .mapIndexed { i, it -> RankedItem(it, 24.6 - i * 4, (90 - i * 12).minutes) },
            albums = previewAlbums().take(3)
                .mapIndexed { i, it -> RankedItem(it, 40.0 - i * 9, (150 - i * 30).minutes) },
            artists = previewArtists().mapIndexed { i, it ->
                RankedItem(
                    it,
                    52.0 - i * 8,
                    (200 - i * 25).minutes
                )
            },
            origins = listOf(
                OriginTime(Origin.YOUTUBE, 530.minutes),
                OriginTime(Origin.LOCAL, 210.minutes),
                OriginTime(Origin.OPEN_SUBSONIC, 72.minutes),
            ),
        ),
        onBack = {},
        onPickRange = {},
        onToggleOrigin = {},
        onOpenSong = {},
        onOpenAlbum = {},
        onOpenArtist = {},
    )
}
