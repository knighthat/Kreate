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
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeTicksTest {

    @Test
    fun ticksStepByRoundMinutes() {
        assertEquals(listOf(0, 5, 10, 15, 20).map { it.minutes }, timeTicks(18.minutes))
        assertEquals(listOf(0, 30, 60, 90, 120).map { it.minutes }, timeTicks(105.minutes))
    }

    @Test
    fun longTimesStepByHours() {
        assertEquals(listOf(0, 1, 2, 3).map { it.hours }, timeTicks(170.minutes))
        assertEquals(listOf(0, 20, 40, 60).map { it.hours }, timeTicks(55.hours))
    }

    @Test
    fun theTopTickReachesTheMost() {
        listOf(1.seconds, 4.minutes, 61.minutes, 7.hours, 999.hours).forEach {
            val ticks = timeTicks(it)
            assert(ticks.last() >= it) { "$ticks for $it" }
            assert(ticks.size in 2..5) { "$ticks for $it" }
        }
    }

    @Test
    fun nothingListenedStillHasAnAxis() {
        assertEquals(listOf(0, 1).map { it.minutes }, timeTicks(0.minutes))
    }
}
