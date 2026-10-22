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

package me.knighthat.kreate3.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plays its items in [shuffled] order while shuffled, in their own otherwise. */
private class ShuffledTimeline(private val shuffled: IntArray) : Timeline() {

    override fun getWindowCount(): Int = shuffled.size

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = when {
        isEmpty -> C.INDEX_UNSET
        shuffleModeEnabled -> shuffled.first()
        else -> 0
    }

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = when {
        isEmpty -> C.INDEX_UNSET
        shuffleModeEnabled -> shuffled.last()
        else -> windowCount - 1
    }

    override fun getNextWindowIndex(
        windowIndex: Int,
        @Player.RepeatMode repeatMode: Int,
        shuffleModeEnabled: Boolean,
    ): Int {
        val order = if (shuffleModeEnabled) shuffled.toList() else List(windowCount) { it }
        val next = order.indexOf(windowIndex) + 1
        return when {
            next < order.size -> order[next]
            repeatMode == Player.REPEAT_MODE_ALL -> order.first()
            else -> C.INDEX_UNSET
        }
    }

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long) =
        window

    override fun getPeriodCount(): Int = shuffled.size

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean) = period

    override fun getIndexOfPeriod(uid: Any): Int = C.INDEX_UNSET

    override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
}

class ShuffledOrderTest {

    @Test
    fun walksTheShuffledOrderFromItsFirst() {
        assertEquals(listOf(2, 0, 3, 1), ShuffledTimeline(intArrayOf(2, 0, 3, 1)).shuffledOrder())
    }

    @Test
    fun emptyTimelineHasNoOrder() {
        assertEquals(emptyList<Int>(), ShuffledTimeline(intArrayOf()).shuffledOrder())
    }

    @Test
    fun singleItem() {
        assertEquals(listOf(0), ShuffledTimeline(intArrayOf(0)).shuffledOrder())
    }
}
