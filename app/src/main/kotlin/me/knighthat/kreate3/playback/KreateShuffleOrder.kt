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

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Where the items the player is about to add go in the shuffled order: right after the item at
 * [anchor], or at random spots when [C.INDEX_UNSET]. Set by [KreatePlayer.playNext] around its
 * add, read by [KreateShuffleOrder.cloneAndInsert].
 */
internal class NextPlacement {

    var anchor: Int = C.INDEX_UNSET
}

/**
 * The order the player shuffles in. Items added at random spots, like ExoPlayer's own
 * `DefaultShuffleOrder`, except the ones added while [NextPlacement.anchor] is set: they go right
 * after the anchor, and are pinned there until [scrambled].
 *
 * Every `cloneAnd…` makes a new order, nothing changes in place.
 *
 * @param shuffled the items' positions in the playlist, in the order they play while shuffled
 * @param pinned positions of the items added as "next" and not scrambled since
 */
@OptIn(UnstableApi::class)
internal class KreateShuffleOrder private constructor(
    private val placement: NextPlacement,
    private val shuffled: IntArray,
    private val pinned: Set<Int>,
    private val random: Random,
) : ShuffleOrder {

    /** Shuffles [length] items. */
    constructor(
        placement: NextPlacement,
        length: Int = 0,
        random: Random = Random,
    ) : this(placement, IntArray(length) { it }.apply { shuffle(random) }, emptySet(), random)

    /** Where each item sits in [shuffled]. */
    private val positions = IntArray(shuffled.size).also { positions ->
        shuffled.forEachIndexed { position, item -> positions[item] = position }
    }

    /** The playlist positions in the order they play while shuffled. */
    val order: List<Int> get() = shuffled.asList()

    /** Positions of the items added as "next" and not scrambled since. */
    val pinnedItems: Set<Int> get() = pinned

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int =
        shuffled.getOrElse(positions[index] + 1) { C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int =
        shuffled.getOrElse(positions[index] - 1) { C.INDEX_UNSET }

    override fun getLastIndex(): Int = shuffled.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = shuffled.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val order = shuffled.mapTo(ArrayList(shuffled.size + insertionCount)) {
            if (it >= insertionIndex) it + insertionCount else it
        }
        val kept = pinned.map { if (it >= insertionIndex) it + insertionCount else it }.toSet()
        val added = insertionIndex until insertionIndex + insertionCount

        // The anchor sits before the insertion, so it kept its position
        val after = placement.anchor.takeIf { it != C.INDEX_UNSET }?.let(order::indexOf) ?: -1
        if (after < 0) {
            for (item in added) order.add(random.nextInt(order.size + 1), item)
            return KreateShuffleOrder(placement, order.toIntArray(), kept, random)
        }

        order.addAll(after + 1, added.toList())
        return KreateShuffleOrder(placement, order.toIntArray(), kept + added, random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexFrom until indexToExclusive
        val count = indexToExclusive - indexFrom
        fun moved(item: Int) = if (item >= indexToExclusive) item - count else item

        return KreateShuffleOrder(
            placement,
            shuffled.filter { it !in removed }.map(::moved).toIntArray(),
            pinned.filter { it !in removed }.map(::moved).toSet(),
            random
        )
    }

    override fun cloneAndClear(): ShuffleOrder =
        KreateShuffleOrder(placement, IntArray(0), emptySet(), random)

    /**
     * The same order with the pinned items among those still to play, the ones after [current],
     * moved to random spots among them, and nothing pinned anymore. Items before [current] have
     * played, they stay.
     */
    fun scrambled(current: Int): KreateShuffleOrder {
        if (pinned.isEmpty()) return this

        val order = shuffled.toMutableList()
        val playing = order.indexOf(current)
        val moving = order.filterIndexed { position, item -> position > playing && item in pinned }
        order.removeAll(moving.toSet())
        for (item in moving.shuffled(random))
            order.add(random.nextInt(playing + 1, order.size + 1), item)
        return KreateShuffleOrder(placement, order.toIntArray(), emptySet(), random)
    }

    /**
     * Plays the items in [positions] order instead, nothing pinned: a restored queue puts its
     * shuffled order back.
     */
    fun restored(positions: IntArray): KreateShuffleOrder =
        KreateShuffleOrder(placement, positions, emptySet(), random)
}
