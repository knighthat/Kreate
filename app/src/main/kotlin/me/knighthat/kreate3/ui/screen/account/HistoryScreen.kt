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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.PeriodSummary
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.AddToQueueButton
import me.knighthat.kreate3.ui.component.ConfigButton
import me.knighthat.kreate3.ui.component.FilterIconButton
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PlayAllButton
import me.knighthat.kreate3.ui.component.PlayNextButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SongMenuAction
import me.knighthat.kreate3.ui.component.SortOrderButton
import me.knighthat.kreate3.ui.component.icon
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.playTapped
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.queue
import me.knighthat.kreate3.ui.component.sourceRes
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The songs the active user played, every play a row, most recent first, in groups by how long
 * ago under headers that stay on top while their group scrolls. A tap plays the song and the ones
 * below it in its group.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val entries = viewModel.entries.collectAsLazyPagingItems()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val selected by viewModel.origins.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val newestFirst by viewModel.newestFirst.collectAsStateWithLifecycle()
    val paused by Preferences.PLAYBACK_HISTORY_PAUSED.state.collectAsStateWithLifecycle()

    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    HistoryContent(
        entries = entries,
        summaries = summaries,
        choices = choices,
        selected = selected,
        range = range,
        newestFirst = newestFirst,
        paused = paused,
        onBack = onBack,
        onToggleOrigin = viewModel::toggleOrigin,
        onPickRange = viewModel::pickRange,
        onToggleOrder = viewModel::toggleOrder,
        onResume = { Preferences.PLAYBACK_HISTORY_PAUSED.update(false) },
        onPlay = { row ->
            scope.launch {
                val songs = viewModel.songsFrom(row)
                // A group can be long, the controller can be called from any thread
                withContext(Dispatchers.Default) {
                    playback.playTapped(resources, row.item, songs, radio = false)
                }?.let(notifier::post)
            }
        },
        onQueue = { action, period ->
            scope.launch {
                val songs = viewModel.songs(period)
                withContext(Dispatchers.Default) { playback.queue(resources, action, songs) }
                    ?.let(notifier::post)
            }
        },
        onDelete = viewModel::delete,
        onClear = viewModel::clear,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/**
 * @param summaries how much was played in each group, by its index
 * @param onPlay plays the song of a row followed by the ones below it in its group
 * @param onQueue does an action with the songs of a group, of every group for `null`
 */
@Composable
private fun HistoryContent(
    entries: LazyPagingItems<HistoryEntry>,
    summaries: Map<Int, PeriodSummary>,
    choices: List<Origin>,
    selected: Set<Origin>,
    range: HistoryRange?,
    newestFirst: Boolean,
    paused: Boolean,
    onBack: () -> Unit,
    onToggleOrigin: (Origin) -> Unit,
    onPickRange: (HistoryRange?) -> Unit,
    onToggleOrder: () -> Unit,
    onResume: () -> Unit,
    onPlay: (HistoryRow) -> Unit,
    onQueue: (QueueAction, period: Int?) -> Unit,
    onDelete: (HistoryRow) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalResources.current
    val filtered = range != null || selected.isNotEmpty()
    val empty = entries.loadState.refresh is LoadState.NotLoading && entries.itemCount == 0
    val listState = rememberLazyListState()

    // Another order or filter starts at the top, coming back to the page keeps the place
    val query = if (entries.itemCount > 0) entries.peek(0)?.query else null
    var shownQuery by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(query) {
        if (query == null || query == shownQuery) return@LaunchedEffect
        if (shownQuery != null) listState.scrollToItem(0)
        shownQuery = query
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }

    if (confirmClear)
        ClearHistoryDialog(
            filtered = filtered,
            onClear = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    if (options)
        HistorySheet(
            choices = choices,
            selected = selected,
            range = range,
            onToggleOrigin = onToggleOrigin,
            onPickRange = onPickRange,
            onQueue = { onQueue(it, null) },
            onClearHistory = { confirmClear = true },
            onDismiss = { options = false },
        )

    DetailScaffold(
        title = stringResource(R.string.screen_title_history),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            SortOrderButton(newestFirst, onToggleOrder)
            ConfigButton({ options = true })
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (paused)
                    item(key = "paused") { PausedBanner(onResume) }

                if (empty)
                    item(key = "empty") {
                        Text(
                            stringResource(
                                if (filtered) R.string.screen_history_status_no_matches
                                else R.string.screen_history_status_nothing_played
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillParentMaxSize()
                                .wrapContentSize(Alignment.Center),
                        )
                    }

                for (index in 0 until entries.itemCount) {
                    when (val entry = entries.peek(index)) {
                        is HistoryHeader -> stickyHeader(key = "${entry.query}:header:${entry.period}") {
                            GroupHeader(
                                bucket = entry.bucket,
                                summary = summaries[entry.period],
                                onQueue = { onQueue(it, entry.period) },
                            )
                        }

                        is HistoryRow -> item(key = "${entry.query}:${entry.playId}") {
                            // Reading the row loads the pages around it
                            val row = entries[index] as? HistoryRow ?: entry
                            SongItem(
                                item = row.item,
                                onClick = { onPlay(row) },
                                menuActions = listOf(
                                    SongMenuAction(
                                        resources.getString(R.string.menu_delete_from_history),
                                        R.drawable.delete,
                                    ) { onDelete(row) }
                                ),
                                trailing = {
                                    Text(row.time, style = MaterialTheme.typography.labelMedium)
                                },
                            )
                        }

                        null -> item { entries[index] }
                    }
                }
            }
        }
    }
}

/**
 * How long ago [bucket] is, with how many plays and songs and for how long of [summary] in small raised text
 * after it, like an exponent, then the quick actions of the group.
 */
@Composable
private fun GroupHeader(
    bucket: HistoryBucket,
    summary: PeriodSummary?,
    onQueue: (QueueAction) -> Unit,
) {
    val resources = LocalResources.current
    val label = when (bucket.period) {
        HistoryPeriod.TODAY -> resources.getString(R.string.section_header_today)
        HistoryPeriod.YESTERDAY -> resources.getString(R.string.section_header_yesterday)
        HistoryPeriod.DAYS -> resources.ago(R.plurals.section_header_days_ago, bucket.count)
        HistoryPeriod.WEEKS -> resources.ago(R.plurals.section_header_weeks_ago, bucket.count)
        HistoryPeriod.MONTHS -> resources.ago(R.plurals.section_header_months_ago, bucket.count)
        HistoryPeriod.YEARS -> resources.ago(R.plurals.section_header_years_ago, bucket.count)
    }
    // Known once its query answers, the group's songs may not all be loaded
    val played = summary?.let {
        resources.getString(
            R.string.section_header_summary,
            resources.getQuantityString(R.plurals.play, it.plays, it.plays),
            resources.getQuantityString(R.plurals.song, it.songs, it.songs),
            resources.timeSpent(it.timeSpent),
        )
    }
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Rows scroll under it
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceDim)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = HEADER_TOP_PADDING),
        ) {
            Text(
                buildAnnotatedString {
                    append(label)
                    if (played != null) {
                        append(' ')
                        withStyle(
                            SpanStyle(
                                fontSize = 0.6.em,
                                baselineShift = BaselineShift.Superscript,
                                color = summaryColor,
                            )
                        ) { append(played) }
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            PlayAllButton({ onQueue(QueueAction.PLAY) })
            PlayNextButton({ onQueue(QueueAction.PLAY_NEXT) })
            AddToQueueButton({ onQueue(QueueAction.ENQUEUE) })
        }
        HorizontalDivider(
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Room above a [GroupHeader], setting a group apart from the rows of the one before. */
private val HEADER_TOP_PADDING = 16.dp

/** History is paused: new plays aren't added until [onResume]. */
@Composable
private fun PausedBanner(onResume: () -> Unit) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(12.dp)
            )
            .padding(start = 16.dp, end = 8.dp),
    ) {
        Text(
            stringResource(R.string.screen_history_status_paused),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onResume) { Text(stringResource(R.string.action_resume)) }
    }

/** Asks before taking plays out, every play unless [filtered], the ones shown then. */
@Composable
private fun ClearHistoryDialog(filtered: Boolean, onClear: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_clear_history)) },
        text = {
            Text(
                stringResource(
                    if (filtered) R.string.dialog_body_clear_history_shown
                    else R.string.dialog_body_clear_history
                )
            )
        },
        confirmButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

/**
 * What can be done with the history shown, in a sheet: Play all, Play next, Add to queue,
 * Shuffle and Clear history as a row of buttons, then the origins to show and the days, by a preset
 * or on the calendar. A filter applies as soon as it's picked; a range on the calendar once its
 * last day is.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistorySheet(
    choices: List<Origin>,
    selected: Set<Origin>,
    range: HistoryRange?,
    onToggleOrigin: (Origin) -> Unit,
    onPickRange: (HistoryRange?) -> Unit,
    onQueue: (QueueAction) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens halfway, on the buttons and filters; the calendar is a drag up
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val zone = remember { TimeZone.getDefault() }
    val today = remember(now, zone) { utcDay(now, zone) }
    val thisYear =
        remember(now, zone) {
            Calendar.getInstance(zone).apply { timeInMillis = now }[Calendar.YEAR]
        }
    val dates = rememberDateRangePickerState(
        initialSelectedStartDateMillis = range?.firstDay,
        initialSelectedEndDateMillis = range?.lastDay,
        selectableDates = remember(today, thisYear) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
                override fun isSelectableYear(year: Int) = year <= thisYear
            }
        },
    )
    val currentRange by rememberUpdatedState(range)
    val currentPick by rememberUpdatedState(onPickRange)

    // A range picked on the calendar shows once it has both ends, a start alone waits for its end
    LaunchedEffect(dates.selectedStartDateMillis, dates.selectedEndDateMillis) {
        val start = dates.selectedStartDateMillis ?: return@LaunchedEffect
        val end = dates.selectedEndDateMillis ?: return@LaunchedEffect
        HistoryRange(start, end).takeIf { it != currentRange }?.let(currentPick)
    }

    // Slides the sheet away before doing what was picked
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val iconOnly = LocalIconOnly.current
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Icon only drops the labels, the icons keep them for screen readers
                @Composable
                fun Action(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) =
                    LabeledIconButton(
                        icon,
                        stringResource(label),
                        showLabel = !iconOnly,
                        onClick = onClick
                    )

                Action(R.drawable.play_arrow, R.string.menu_play_all) {
                    close { onQueue(QueueAction.PLAY) }
                }
                Action(R.drawable.queue_play_next, R.string.menu_play_next) {
                    close { onQueue(QueueAction.PLAY_NEXT) }
                }
                Action(R.drawable.playlist_add, R.string.menu_add_to_queue) {
                    close { onQueue(QueueAction.ENQUEUE) }
                }
                Action(R.drawable.shuffle, R.string.menu_shuffle) {
                    close { onQueue(QueueAction.SHUFFLE) }
                }
                Action(R.drawable.delete, R.string.menu_clear_history) { close(onClearHistory) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (choices.isNotEmpty()) {
                SheetHeader(stringResource(R.string.section_header_source))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    choices.forEach {
                        if (iconOnly)
                            FilterIconButton(
                                it.icon,
                                stringResource(it.sourceRes),
                                selected = it in selected,
                                onToggle = { onToggleOrigin(it) },
                            )
                        else FilterChip(
                            selected = it in selected,
                            onClick = { onToggleOrigin(it) },
                            label = { Text(stringResource(it.sourceRes)) },
                            leadingIcon = {
                                // The label says the same, nothing more for screen readers
                                Icon(
                                    painter = painterResource(it.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }

            SheetHeader(stringResource(R.string.section_header_date_range))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                HistoryRangePreset.entries.forEach { preset ->
                    val days = preset.range(now, zone)
                    FilterChip(
                        selected = days == range,
                        onClick = {
                            dates.setSelection(days?.firstDay, days?.lastDay)
                            days?.let { dates.displayedMonthMillis = it.firstDay }
                            // Every day isn't a selection of the calendar, it's applied here
                            if (days == null) onPickRange(null)
                        },
                        label = { Text(stringResource(preset.labelRes)) },
                    )
                }
            }
            // The calendar scrolls by itself, inside the sheet it needs a height of its own
            DateRangePicker(
                state = dates,
                title = null,
                showModeToggle = false,
                // The sheet's color, the picker's own is for a dialog
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.height(DATE_PICKER_HEIGHT),
            )
        }
    }
}

/** Names a part of the [HistorySheet]. */
@Composable
private fun SheetHeader(title: String) =
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )

/** Height of the calendar in the [HistorySheet], a few weeks of a month and its headline. */
private val DATE_PICKER_HEIGHT = 480.dp

private fun Resources.ago(plural: Int, count: Int): String = getQuantityString(plural, count, count)

/** [time] in minutes, in hours and minutes from an hour, in seconds under a minute. */
internal fun Resources.timeSpent(time: Duration): String {
    if (time < 1.minutes) {
        val seconds = time.inWholeSeconds.toInt()
        return getQuantityString(R.plurals.second, seconds, seconds)
    }
    val hours = time.inWholeHours.toInt()
    val minutes = (time.inWholeMinutes % 60).toInt()
    val inMinutes = getQuantityString(R.plurals.minute, minutes, minutes)
    if (hours == 0) return inMinutes

    val inHours = getQuantityString(R.plurals.hour, hours, hours)
    return if (minutes == 0) inHours else "$inHours $inMinutes"
}

@ScreenPreviews
@Composable
private fun HistoryPreview() = PreviewTheme {
    val songs = previewSongs()
    fun row(period: Int, index: Int, time: String) =
        HistoryRow(period, index.toLong(), songs[index % songs.size], time, query = 1)

    HistoryContent(
        entries = flowOf(
            PagingData.from(
                listOf(
                    HistoryHeader(0, HistoryBucket(HistoryPeriod.TODAY, 0), query = 1),
                    row(0, 0, "9:41 PM"),
                    row(0, 1, "7:30 PM"),
                    row(0, 2, "6:02 PM"),
                    HistoryHeader(1, HistoryBucket(HistoryPeriod.WEEKS, 2), query = 1),
                    row(1, 3, "8:01 PM"),
                    row(1, 4, "9:12 AM"),
                )
            )
        ).collectAsLazyPagingItems(),
        summaries = mapOf(
            0 to PeriodSummary(0, 3, 2, 18.minutes),
            1 to PeriodSummary(1, 2, 2, 72.minutes),
        ),
        choices = listOf(Origin.LOCAL, Origin.YOUTUBE),
        selected = emptySet(),
        range = null,
        newestFirst = true,
        paused = true,
        onBack = {},
        onToggleOrigin = {},
        onPickRange = {},
        onToggleOrder = {},
        onResume = {},
        onPlay = {},
        onQueue = { _, _ -> },
        onDelete = {},
        onClear = {},
    )
}
