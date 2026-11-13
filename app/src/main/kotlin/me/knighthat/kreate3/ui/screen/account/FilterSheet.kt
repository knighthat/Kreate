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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.FilterIconButton
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.icon
import me.knighthat.kreate3.ui.component.sourceRes
import java.util.Calendar
import java.util.TimeZone

/** A ready-made choice of a [DateRangeFilter]: its label and its days, `null` for every day. */
@Immutable
data class RangePreset(@StringRes val label: Int, val range: HistoryRange?)

/** Height of the calendar in a [DateRangeFilter], a few weeks of a month and its headline. */
private val DATE_PICKER_HEIGHT = 480.dp

/** Names a part of a filter sheet, e.g. History's or Statistics'. */
@Composable
internal fun SheetHeader(title: String) =
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )

/**
 * The Source part of a filter sheet: a chip per origin of [choices], icon buttons while Icon only,
 * selected for those in [selected]. Nothing while there are no [choices].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OriginFilter(
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggle: (Origin) -> Unit,
) {
    if (choices.isEmpty()) return

    val iconOnly = LocalIconOnly.current
    SheetHeader(stringResource(R.string.section_header_source))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        choices.forEach {
            if (iconOnly) {
                FilterIconButton(
                    it.icon,
                    stringResource(it.sourceRes),
                    selected = it in selected,
                    onToggle = { onToggle(it) },
                )
            } else {
                FilterChip(
                    selected = it in selected,
                    onClick = { onToggle(it) },
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
}

/**
 * The Date range part of a filter sheet: a chip per preset, then a calendar up to today. A preset
 * applies at once, a range on the calendar once its last day is picked.
 *
 * @param range the days shown, `null` for every day
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DateRangeFilter(
    range: HistoryRange?,
    presets: List<RangePreset>,
    onPick: (HistoryRange?) -> Unit,
) {
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
    val currentPick by rememberUpdatedState(onPick)

    // A range picked on the calendar shows once it has both ends, a start alone waits for its end
    LaunchedEffect(dates.selectedStartDateMillis, dates.selectedEndDateMillis) {
        val start = dates.selectedStartDateMillis ?: return@LaunchedEffect
        val end = dates.selectedEndDateMillis ?: return@LaunchedEffect
        HistoryRange(start, end).takeIf { it != currentRange }?.let(currentPick)
    }

    SheetHeader(stringResource(R.string.section_header_date_range))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        presets.forEach { preset ->
            val days = preset.range
            FilterChip(
                selected = days == range,
                onClick = {
                    dates.setSelection(days?.firstDay, days?.lastDay)
                    days?.let { dates.displayedMonthMillis = it.firstDay }
                    // Every day isn't a selection of the calendar, it's applied here
                    if (days == null) onPick(null)
                },
                label = { Text(stringResource(preset.label)) },
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
