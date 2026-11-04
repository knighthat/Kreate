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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDiffTest {

    /** Applies [changes] to [old] the way the saved queue does, [added] fills the inserts. */
    private fun apply(old: List<Int>, new: List<Int>, changes: List<QueueChange>): List<Int> {
        val list = old.toMutableList()
        for (change in changes)
            when (change) {
                is QueueChange.Remove -> repeat(change.count) { list.removeAt(change.index) }
                is QueueChange.Move -> {
                    val block = List(change.count) { list.removeAt(change.index) }
                    list.addAll(change.to, block)
                }

                is QueueChange.Insert ->
                    list.addAll(
                        change.index,
                        new.subList(change.index, change.index + change.count)
                    )

                QueueChange.Replace -> return new
            }
        return list
    }

    private fun check(old: List<Int>, new: List<Int>): List<QueueChange> {
        val changes = diffQueue(old, new)
        assertEquals(new, apply(old, new, changes))
        return changes
    }

    @Test
    fun nothingChanged() {
        assertTrue(check(listOf(1, 2, 3), listOf(1, 2, 3)).isEmpty())
    }

    @Test
    fun songsAddedAtTheEndAndInTheMiddle() {
        val end = check(listOf(1, 2), listOf(1, 2, 3, 4))
        assertEquals(1, end.size)
        val middle = check(listOf(1, 2), listOf(1, 3, 2, 4))
        assertEquals(2, middle.size)
    }

    @Test
    fun songsRemoved() {
        assertEquals(1, check(listOf(1, 2, 3, 4), listOf(1, 4)).size)
        assertEquals(2, check(listOf(1, 2, 3, 4, 5), listOf(2, 3, 5)).size)
        assertEquals(1, check(listOf(1, 2, 3), emptyList()).size)
    }

    @Test
    fun oneSongMovedIsOneMove() {
        val down = check(listOf(1, 2, 3, 4, 5), listOf(1, 3, 4, 2, 5))
        assertEquals(1, down.size)
        assertTrue(down.single() is QueueChange.Move)
        val up = check(listOf(1, 2, 3, 4, 5), listOf(5, 1, 2, 3, 4))
        assertEquals(1, up.size)
        assertEquals(1, check(listOf(1, 2, 3, 4, 5), listOf(2, 3, 4, 5, 1)).size)
    }

    @Test
    fun aBlockMovedIsOneMove() {
        assertEquals(1, check(listOf(1, 2, 3, 4, 5, 6), listOf(1, 4, 5, 2, 3, 6)).size)
    }

    @Test
    fun aMoveWithRemovalsAndInsertions() {
        check(listOf(1, 2, 3, 4, 5), listOf(9, 4, 1, 2, 3, 8))
        check(listOf(1, 2, 3, 4, 5), listOf(7, 5, 1, 4, 9))
    }

    @Test
    fun aShuffleIsWrittenWhole() {
        assertEquals(
            listOf(QueueChange.Replace),
            check(listOf(1, 2, 3, 4, 5), listOf(3, 1, 5, 2, 4))
        )
    }

    @Test
    fun theWholeQueueReplaced() {
        val changes = check(listOf(1, 2, 3), listOf(4, 5, 6))
        assertEquals(2, changes.size)
    }
}
