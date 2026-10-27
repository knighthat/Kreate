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

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.ui.screen.account.HistoryBuckets.Companion.MAX_YEARS
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Instant

/** A day in milliseconds. */
private const val DAY = 24 * 60 * 60 * 1000L

/** How far back the plays of a [HistoryBucket] are. */
enum class HistoryPeriod { TODAY, YESTERDAY, DAYS, WEEKS, MONTHS, YEARS }

/**
 * A group of the history: plays [count] [period]s ago, `0` for [HistoryPeriod.TODAY] and `1` for
 * [HistoryPeriod.YESTERDAY].
 */
@Immutable
data class HistoryBucket(val period: HistoryPeriod, val count: Int)

/**
 * Puts plays in [HistoryBucket]s as seen at [now] (epoch millis), by the calendar of [zone]. The
 * further back, the coarser: a day each up to 6 days ago, a week each until a month ago, a month
 * each until a year ago, then a year each. Not for several threads at once.
 */
internal class HistoryBuckets(now: Long, private val zone: TimeZone) {

    private val today = Calendar.getInstance(zone).apply { startOfDay(now) }
    private val day = Calendar.getInstance(zone)

    /** The bucket of a play at [time], epoch millis. Plays after [now] are today's. */
    fun of(time: Long): HistoryBucket {
        day.startOfDay(time)
        // Rounded, the day the clocks change is an hour shorter or longer
        val days = ((today.timeInMillis - day.timeInMillis + DAY / 2) / DAY).toInt()
        val months = (today[Calendar.YEAR] - day[Calendar.YEAR]) * 12 +
                today[Calendar.MONTH] - day[Calendar.MONTH] -
                if (today[Calendar.DAY_OF_MONTH] < day[Calendar.DAY_OF_MONTH]) 1 else 0

        return when {
            days <= 0 -> HistoryBucket(HistoryPeriod.TODAY, 0)
            days == 1 -> HistoryBucket(HistoryPeriod.YESTERDAY, 1)
            days < 7 -> HistoryBucket(HistoryPeriod.DAYS, days)
            months < 1 -> HistoryBucket(HistoryPeriod.WEEKS, days / 7)
            months < 12 -> HistoryBucket(HistoryPeriod.MONTHS, months)
            else -> HistoryBucket(HistoryPeriod.YEARS, months / 12)
        }
    }

    /**
     * Every bucket with the time it spans, the most recent first, side by side with no gaps.
     * Today's goes on without end, the one [MAX_YEARS] years ago back to the beginning of time.
     */
    fun spans(): List<HistorySpan> {
        val spans = mutableListOf<HistorySpan>()
        val cursor = today.clone() as Calendar
        var bucket = of(cursor.timeInMillis)
        var start = cursor.timeInMillis
        var end = Long.MAX_VALUE

        // A day at a time up to a year ago, buckets are uneven until then
        while (bucket.period != HistoryPeriod.YEARS) {
            cursor.add(Calendar.DAY_OF_MONTH, -1)
            val older = of(cursor.timeInMillis)
            if (older != bucket) {
                spans += HistorySpan(bucket, start, end)
                end = start
                bucket = older
            }
            start = cursor.timeInMillis
        }
        // The year k ago starts the day after the date (k + 1) years before today
        for (years in 1..MAX_YEARS) {
            val yearStart =
                if (years == MAX_YEARS) Long.MIN_VALUE
                else (today.clone() as Calendar).apply {
                    add(Calendar.MONTH, -12 * (years + 1))
                    add(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
            spans += HistorySpan(HistoryBucket(HistoryPeriod.YEARS, years), yearStart, end)
            end = yearStart
        }
        return spans
    }

    private companion object {
        /** Years back the history has groups for, older plays join the oldest. */
        const val MAX_YEARS = 50
    }
}

/**
 * The plays of [bucket], from [start] up to, not including, [end], epoch millis.
 */
@Immutable
data class HistorySpan(val bucket: HistoryBucket, val start: Long, val end: Long) {

    val period: PlayPeriod
        get() = PlayPeriod(Instant.fromEpochMilliseconds(start), Instant.fromEpochMilliseconds(end))

    /** The part of the span in [range] in [zone], `null` when there is none. */
    fun clip(range: HistoryRange?, zone: TimeZone): HistorySpan? {
        if (range == null) return this
        val clipped =
            copy(start = maxOf(start, range.start(zone)), end = minOf(end, range.end(zone)))
        return clipped.takeIf { it.start < it.end }
    }
}

/** A row of the history: the header of a group, or a song of it. */
@Immutable
sealed interface HistoryEntry {
    /** The index of the group in the spans the history shows. */
    val period: Int

    /**
     * The query the entry came from, a new one for every order or filter: the same group or song
     * in another query is another entry, the list doesn't follow it there.
     */
    val query: Int
}

/** The header of the group [period], the plays of [bucket]. */
@Immutable
data class HistoryHeader(
    override val period: Int,
    val bucket: HistoryBucket,
    override val query: Int,
) : HistoryEntry

/**
 * A play of the group [period]: the song played, and [time] when.
 *
 * @property playId the play's id in the history
 */
@Immutable
class HistoryRow(
    override val period: Int,
    val playId: Long,
    val item: MediaItem,
    val time: String,
    override val query: Int,
) : HistoryEntry

/** Ready-made ranges of the history's date picker. */
enum class HistoryRangePreset { ALL_TIME, TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_YEAR }

/**
 * Days of the history to show, from [firstDay] to [lastDay] included, as a date picker gives
 * them: the UTC midnight of each.
 */
@Immutable
data class HistoryRange(val firstDay: Long, val lastDay: Long) {

    /** The first instant of the range in [zone], epoch millis. */
    fun start(zone: TimeZone): Long = localMidnight(firstDay, zone)

    /** The first instant after the range in [zone], epoch millis. */
    fun end(zone: TimeZone): Long = localMidnight(lastDay + DAY, zone)

    /** The preset that picks the same days at [now] in [zone], `null` when none does. */
    fun preset(now: Long, zone: TimeZone): HistoryRangePreset? =
        HistoryRangePreset.entries.firstOrNull { it.range(now, zone) == this }
}

/** The days of the preset at [now] in [zone], `null` for every day. */
fun HistoryRangePreset.range(now: Long, zone: TimeZone): HistoryRange? {
    val today = utcDay(now, zone)
    return when (this) {
        HistoryRangePreset.ALL_TIME -> null
        HistoryRangePreset.TODAY -> HistoryRange(today, today)
        HistoryRangePreset.LAST_7_DAYS -> HistoryRange(today - 6 * DAY, today)
        HistoryRangePreset.LAST_30_DAYS -> HistoryRange(today - 29 * DAY, today)
        HistoryRangePreset.THIS_YEAR -> {
            val year = Calendar.getInstance(zone).apply { timeInMillis = now }[Calendar.YEAR]
            val first = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(year, Calendar.JANUARY, 1)
            }
            HistoryRange(first.timeInMillis, today)
        }
    }
}

/** The UTC midnight of the date of [time] in [zone], how a date picker gives that day. */
fun utcDay(time: Long, zone: TimeZone): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = time }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local[Calendar.YEAR], local[Calendar.MONTH], local[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

/** The midnight in [zone] of the day a date picker gives as [utcDay]. */
private fun localMidnight(utcDay: Long, zone: TimeZone): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
    return Calendar.getInstance(zone).apply {
        clear()
        set(utc[Calendar.YEAR], utc[Calendar.MONTH], utc[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

private fun Calendar.startOfDay(time: Long) {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}
