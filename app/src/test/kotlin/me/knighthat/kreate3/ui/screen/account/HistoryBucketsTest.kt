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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryBucketsTest {

    private val zone: TimeZone = TimeZone.getTimeZone("America/New_York")

    /** Epoch millis of a local time in [zone], [month] from 1. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private val now = at(2026, 10, 27, 9, 30)
    private val buckets = HistoryBuckets(now, zone)

    private fun bucket(time: Long) = buckets.of(time)

    @Test
    fun `days split at local midnight`() {
        assertEquals(HistoryBucket(HistoryPeriod.TODAY, 0), bucket(at(2026, 10, 27, 0, 0)))
        assertEquals(HistoryBucket(HistoryPeriod.YESTERDAY, 1), bucket(at(2026, 10, 26, 23, 59)))
        assertEquals(HistoryBucket(HistoryPeriod.DAYS, 2), bucket(at(2026, 10, 25)))
        assertEquals(HistoryBucket(HistoryPeriod.DAYS, 6), bucket(at(2026, 10, 21)))
    }

    @Test
    fun `later plays are today's`() {
        assertEquals(HistoryBucket(HistoryPeriod.TODAY, 0), bucket(at(2026, 10, 27, 23, 0)))
    }

    @Test
    fun `weeks until a month ago`() {
        assertEquals(HistoryBucket(HistoryPeriod.WEEKS, 1), bucket(at(2026, 10, 20)))
        assertEquals(HistoryBucket(HistoryPeriod.WEEKS, 2), bucket(at(2026, 10, 13)))
        // 28 days, a month back from the 27th is Sep 27
        assertEquals(HistoryBucket(HistoryPeriod.WEEKS, 4), bucket(at(2026, 9, 29)))
        assertEquals(HistoryBucket(HistoryPeriod.MONTHS, 1), bucket(at(2026, 9, 27)))
    }

    @Test
    fun `months until a year ago, then years`() {
        assertEquals(HistoryBucket(HistoryPeriod.MONTHS, 1), bucket(at(2026, 8, 28)))
        assertEquals(HistoryBucket(HistoryPeriod.MONTHS, 2), bucket(at(2026, 8, 27)))
        assertEquals(HistoryBucket(HistoryPeriod.MONTHS, 11), bucket(at(2025, 10, 28)))
        assertEquals(HistoryBucket(HistoryPeriod.YEARS, 1), bucket(at(2025, 10, 27)))
        assertEquals(HistoryBucket(HistoryPeriod.YEARS, 2), bucket(at(2024, 1, 1)))
    }

    @Test
    fun `a day the clocks change is still one day`() {
        // Clocks went back on Nov 1, 2026 in New York
        val after = HistoryBuckets(at(2026, 11, 2, 0, 30), zone)
        assertEquals(HistoryBucket(HistoryPeriod.YESTERDAY, 1), after.of(at(2026, 11, 1, 0, 30)))
        assertEquals(HistoryBucket(HistoryPeriod.DAYS, 2), after.of(at(2026, 10, 31, 23, 30)))
    }

    @Test
    fun `presets pick days up to today`() {
        val today = utcDay(now, zone)
        val day = 24 * 60 * 60 * 1000L

        assertNull(HistoryRangePreset.ALL_TIME.range(now, zone))
        assertEquals(HistoryRange(today, today), HistoryRangePreset.TODAY.range(now, zone))
        assertEquals(
            HistoryRange(today - 6 * day, today),
            HistoryRangePreset.LAST_7_DAYS.range(now, zone)
        )
        assertEquals(
            HistoryRangePreset.LAST_30_DAYS,
            HistoryRange(today - 29 * day, today).preset(now, zone)
        )
    }

    @Test
    fun `a range covers its days in the local zone`() {
        val range = HistoryRangePreset.TODAY.range(now, zone)!!

        assertEquals(at(2026, 10, 27, 0, 0), range.start(zone))
        assertEquals(at(2026, 10, 28, 0, 0), range.end(zone))
    }

    @Test
    fun `spans follow each other back in time, each in its own bucket`() {
        val spans = buckets.spans()

        assertEquals(
            HistorySpan(
                HistoryBucket(HistoryPeriod.TODAY, 0),
                at(2026, 10, 27, 0, 0),
                Long.MAX_VALUE
            ), spans.first()
        )
        assertEquals(Long.MIN_VALUE, spans.last().start)
        spans.zipWithNext { newer, older -> assertEquals(newer.start, older.end) }
        assertEquals(spans.size, spans.map { it.bucket }.toSet().size)
        // Both ends of every span but the open ones fall in its bucket
        spans.drop(1).dropLast(1).forEach {
            assertEquals(it.bucket, buckets.of(it.start))
            assertEquals(it.bucket, buckets.of(it.end - 1))
        }
        assertEquals(
            listOf(1, 2, 3, 4),
            spans.filter { it.bucket.period == HistoryPeriod.WEEKS }.map { it.bucket.count },
        )
    }

    @Test
    fun `a span is clipped to the range`() {
        val today = HistoryRangePreset.TODAY.range(now, zone)
        val span = buckets.spans().first()

        assertEquals(at(2026, 10, 28, 0, 0), span.clip(today, zone)!!.end)
        assertNull(buckets.spans()[1].clip(today, zone))
    }
}
