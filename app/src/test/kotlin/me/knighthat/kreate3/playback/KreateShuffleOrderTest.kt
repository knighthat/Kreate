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
import androidx.media3.exoplayer.source.ShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KreateShuffleOrderTest {

    private val placement = NextPlacement()

    /** Shuffled as [order], none pinned. */
    private fun orderOf(vararg order: Int) =
        KreateShuffleOrder(placement).restored(order)

    private fun ShuffleOrder.walk(): List<Int> = buildList {
        var index = firstIndex
        while (index != C.INDEX_UNSET) {
            add(index)
            index = getNextIndex(index)
        }
    }

    /** Adds [count] items at [at] as `playNext` does, after the item at [at] - 1. */
    private fun ShuffleOrder.addNext(at: Int, count: Int): KreateShuffleOrder {
        placement.anchor = at - 1
        try {
            return cloneAndInsert(at, count) as KreateShuffleOrder
        } finally {
            placement.anchor = C.INDEX_UNSET
        }
    }

    @Test
    fun walksForwardAndBack() {
        val order = orderOf(2, 0, 3, 1)
        assertEquals(listOf(2, 0, 3, 1), order.walk())
        assertEquals(C.INDEX_UNSET, order.getNextIndex(1))
        assertEquals(C.INDEX_UNSET, order.getPreviousIndex(2))
        assertEquals(3, order.getPreviousIndex(1))
        assertEquals(1, order.lastIndex)
    }

    @Test
    fun emptyOrderHasNoEnds() {
        val order = KreateShuffleOrder(placement)
        assertEquals(C.INDEX_UNSET, order.firstIndex)
        assertEquals(C.INDEX_UNSET, order.lastIndex)
    }

    @Test
    fun nextItemsPlayRightAfterTheAnchorInTheirOrder() {
        // Playing item 3, which sits at the second spot
        val order = orderOf(2, 3, 0, 1).addNext(at = 4, count = 2)
        assertEquals(listOf(2, 3, 4, 5, 0, 1), order.walk())
        assertEquals(setOf(4, 5), order.pinnedItems)
    }

    @Test
    fun nextItemsInTheMiddleOfThePlaylistShiftTheOthers() {
        // Playing item 0, the new ones take positions 1 and 2
        val order = orderOf(1, 0, 2).addNext(at = 1, count = 2)
        assertEquals(listOf(3, 0, 1, 2, 4), order.walk())
        assertEquals(setOf(1, 2), order.pinnedItems)
    }

    @Test
    fun itemsAddedWithoutAnAnchorAreNotPinned() {
        val order = orderOf(1, 0).cloneAndInsert(1, 2) as KreateShuffleOrder
        assertEquals(listOf(0, 1, 2, 3), order.walk().sorted())
        assertTrue(order.pinnedItems.isEmpty())
    }

    @Test
    fun removingItemsShiftsTheOrderAndThePins() {
        val order = orderOf(1, 0, 2).addNext(at = 1, count = 1)
        // The new item is at position 1, removing the first item moves it to 0
        val removed = order.cloneAndRemove(0, 1) as KreateShuffleOrder
        assertEquals(listOf(0, 1, 2), removed.walk().sorted())
        assertEquals(setOf(0), removed.pinnedItems)
    }

    @Test
    fun removingAPinnedItemUnpinsIt() {
        val order = orderOf(1, 0).addNext(at = 1, count = 1)
        val removed = order.cloneAndRemove(1, 2) as KreateShuffleOrder
        assertTrue(removed.pinnedItems.isEmpty())
        assertEquals(2, removed.length)
    }

    @Test
    fun clearingForgetsEverything() {
        val order = orderOf(1, 0).addNext(at = 1, count = 1).cloneAndClear()
        assertEquals(0, order.length)
        assertEquals(C.INDEX_UNSET, order.firstIndex)
    }

    @Test
    fun scramblingWithNothingPinnedChangesNothing() {
        val order = orderOf(2, 0, 1)
        assertSame(order, order.scrambled(2))
    }

    @Test
    fun scramblingMovesPinnedItemsOnlyAmongThoseStillToPlay() {
        repeat(50) { seed ->
            // Playing item 0, the second to play, the new items 1 and 2 follow it
            val order = KreateShuffleOrder(placement, random = Random(seed))
                .restored(intArrayOf(2, 0, 3, 1))
                .addNext(at = 1, count = 2)
            assertEquals(listOf(4, 0, 1, 2, 5, 3), order.walk())

            val scrambled = order.scrambled(current = 0)

            val walk = scrambled.walk()
            assertEquals((0..5).toList(), walk.sorted())
            // What played and the playing item stay
            assertEquals(listOf(4, 0), walk.take(2))
            assertTrue(scrambled.pinnedItems.isEmpty())
        }
    }

    @Test
    fun scramblingCanMoveAPinnedItemAwayFromNext() {
        val moved = (0 until 50).any { seed ->
            val order = KreateShuffleOrder(placement, random = Random(seed))
                .restored(intArrayOf(0, 1, 2, 3, 4, 5))
                .addNext(at = 1, count = 1)
            order.scrambled(current = 0).getNextIndex(0) != 1
        }
        assertTrue(moved)
    }

    @Test
    fun scrambledOrderKeepsEveryItemOnce() {
        val order = orderOf(0, 1, 2, 3, 4).addNext(at = 1, count = 3)
        val scrambled = order.scrambled(current = 0)
        assertEquals((0..7).toList(), scrambled.walk().sorted())
        assertEquals(0, scrambled.firstIndex)
    }
}
