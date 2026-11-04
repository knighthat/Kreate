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

/** A change to a queue, see [diffQueue]. Indexes are those of the queue when the change is made. */
internal sealed interface QueueChange {

    class Remove(val index: Int, val count: Int) : QueueChange

    /** Moves the block so that it starts at [to] of the queue left without it. */
    class Move(val index: Int, val count: Int, val to: Int) : QueueChange

    /** [count] new items starting at [index] of the new queue. */
    class Insert(val index: Int, val count: Int) : QueueChange

    /** The queue was rearranged in a way no single move explains: write it whole. */
    data object Replace : QueueChange
}

/**
 * The changes that turn [old] into [new], in the order to make them: songs removed, one block
 * moved, songs inserted. Items are told apart by their identity (the window uids of the
 * timeline), so a song queued twice and a move are told apart from an insert and a remove.
 */
internal fun <T : Any> diffQueue(old: List<T>, new: List<T>): List<QueueChange> {
    val oldSet = old.toHashSet()
    val newSet = new.toHashSet()
    val changes = ArrayList<QueueChange>()

    // From the end, so an index stays valid after the removals before it were made
    var index = old.size - 1
    while (index >= 0) {
        if (old[index] in newSet) {
            index--
            continue
        }
        val end = index
        while (index >= 0 && old[index] !in newSet) index--
        changes += QueueChange.Remove(index + 1, end - index)
    }

    val before = old.filter { it in newSet }
    val after = new.filter { it in oldSet }
    if (before != after) {
        val move = rotation(before, after) ?: return listOf(QueueChange.Replace)
        changes += move
    }

    index = 0
    while (index < new.size) {
        if (new[index] in oldSet) {
            index++
            continue
        }
        val start = index
        while (index < new.size && new[index] !in oldSet) index++
        changes += QueueChange.Insert(start, index - start)
    }
    return changes
}

/**
 * The move that turns [before] into [after] when what lies between their common start and end is
 * a block swapped with the rest (`X Y` to `Y X`), what `moveMediaItem(s)` does. `null` when it
 * is anything else.
 */
private fun <T : Any> rotation(before: List<T>, after: List<T>): QueueChange.Move? {
    var start = 0
    while (before[start] == after[start]) start++
    var end = before.size
    while (before[end - 1] == after[end - 1]) end--

    val size = end - start
    val split = (start until end).first { before[it] == after[start] } - start
    if (split <= 0) return null
    for (offset in 0 until size)
        if (before[start + (split + offset) % size] != after[start + offset]) return null

    val tail = size - split
    return if (split <= tail)
        QueueChange.Move(start, split, start + tail)
    else
        QueueChange.Move(start + split, tail, start)
}
