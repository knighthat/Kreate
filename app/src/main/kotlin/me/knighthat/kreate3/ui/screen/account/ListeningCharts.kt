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

import android.content.Context
import android.text.format.DateUtils
import me.knighthat.kreate3.database.model.PlaybackHistory
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration

/**
 * A bar of a chart.
 *
 * @property label the bar's name on the axis; the chart picks which to show
 * @property detail what the chart says when the bar is tapped
 */
data class ChartBar(val value: Float, val label: String, val detail: String)

/** The time listened in some plays over the days, by hour and by day of the week. */
data class ListeningCharts(
    val overTime: List<ChartBar>,
    val byHour: List<ChartBar>,
    val byWeekday: List<ChartBar>,
)

/** The [ListeningCharts] of [plays], the oldest first, up to [now] in [zone]. */
internal fun Context.listeningCharts(
    plays: List<PlaybackHistory>,
    now: Long,
    zone: TimeZone,
): ListeningCharts = ListeningCharts(
    overTime = overTime(plays, now, zone),
    byHour = byHour(plays, zone),
    byWeekday = byWeekday(plays, zone),
)

/**
 * Time listened per day from the first play to today, per week past a month, per month past
 * half a year.
 */
private fun Context.overTime(
    plays: List<PlaybackHistory>,
    now: Long,
    zone: TimeZone
): List<ChartBar> {
    val first = plays.firstOrNull()?.createdAt?.toEpochMilliseconds() ?: return emptyList()
    val days = (now - first) / DateUtils.DAY_IN_MILLIS
    val step = when {
        days <= DAYS_SHOWN -> Calendar.DAY_OF_MONTH
        days <= WEEKS_SHOWN * 7 -> Calendar.WEEK_OF_YEAR
        else -> Calendar.MONTH
    }
    val format = if (step == Calendar.MONTH)
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or
                DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR
    else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH

    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = first
        startOfDay()
        when (step) {
            Calendar.WEEK_OF_YEAR -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            Calendar.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val bars = mutableListOf<Pair<Long, Duration>>()
    var index = 0
    while (calendar.timeInMillis <= now) {
        val start = calendar.timeInMillis
        calendar.add(step, 1)
        val end = calendar.timeInMillis
        var spent = Duration.ZERO
        while (index < plays.size && plays[index].createdAt.toEpochMilliseconds() < end) {
            spent += plays[index++].timeSpent
        }
        bars += start to spent
    }
    return bars.mapIndexed { i, (start, spent) ->
        val date = DateUtils.formatDateTime(this, start, format)
        ChartBar(
            value = spent.inWholeSeconds.toFloat(),
            label = date,
            detail = detail(date, spent),
        )
    }
}

/** Time listened by the hour the plays started. */
private fun Context.byHour(plays: List<PlaybackHistory>, zone: TimeZone): List<ChartBar> {
    val calendar = Calendar.getInstance(zone)
    val spent = Array(HOURS) { Duration.ZERO }
    plays.forEach {
        calendar.timeInMillis = it.createdAt.toEpochMilliseconds()
        spent[calendar.get(Calendar.HOUR_OF_DAY)] += it.timeSpent
    }
    calendar.startOfDay()
    return spent.mapIndexed { hour, time ->
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        val label =
            DateUtils.formatDateTime(this, calendar.timeInMillis, DateUtils.FORMAT_SHOW_TIME)
        ChartBar(
            value = time.inWholeSeconds.toFloat(),
            label = label,
            detail = detail(label, time),
        )
    }
}

/** Time listened by the day of the week the plays started, from the locale's first day. */
private fun Context.byWeekday(plays: List<PlaybackHistory>, zone: TimeZone): List<ChartBar> {
    val calendar = Calendar.getInstance(zone)
    val spent = mutableMapOf<Int, Duration>()
    plays.forEach {
        calendar.timeInMillis = it.createdAt.toEpochMilliseconds()
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        spent[day] = (spent[day] ?: Duration.ZERO) + it.timeSpent
    }
    val symbols = DateFormatSymbols.getInstance()
    return (0 until 7).map { offset ->
        // Calendar's days go from SUNDAY = 1 to SATURDAY = 7
        val day = (calendar.firstDayOfWeek - 1 + offset) % 7 + 1
        val time = spent[day] ?: Duration.ZERO
        ChartBar(
            value = time.inWholeSeconds.toFloat(),
            label = symbols.shortWeekdays[day],
            detail = detail(symbols.weekdays[day], time),
        )
    }
}

private fun Context.detail(label: String, time: Duration) = "$label · ${resources.timeSpent(time)}"

private fun Calendar.startOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private const val HOURS = 24

/** Up to this many days of plays get a bar per day. */
private const val DAYS_SHOWN = 30

/** Up to this many weeks of plays get a bar per week, longer get one per month. */
private const val WEEKS_SHOWN = 26
