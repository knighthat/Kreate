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

package me.knighthat.kreate3.ui.screen.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YouTubePlaylistMovesTest {

    /** What YouTube does with each move: the id goes right before another, or to the end. */
    private fun apply(from: List<String>, moves: List<Pair<String, String?>>): List<String> {
        val list = from.toMutableList()
        moves.forEach { (id, before) ->
            list.remove(id)
            list.add(before?.let { list.indexOf(it) } ?: list.size, id)
        }
        return list
    }

    @Test
    fun anOrderThatDidNotChangeNeedsNoMove() {
        assertEquals(emptyList<Pair<String, String?>>(), moves(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun oneSongMovedIsOneMove() {
        val from = listOf("a", "b", "c", "d", "e")
        val to = listOf("b", "c", "d", "a", "e")

        val made = moves(from, to)

        assertEquals(1, made.size)
        assertEquals(to, apply(from, made))
    }

    @Test
    fun theLastSongGoesToTheEnd() {
        val from = listOf("a", "b", "c")
        val to = listOf("a", "c", "b")

        assertEquals(to, apply(from, moves(from, to)))
    }

    @Test
    fun anyOrderComesOutRightWithNoMoreMovesThanSongsLeftOut() {
        val random = Random(7)
        repeat(300) {
            val from = (0 until random.nextInt(1, 40)).map { "s$it" }
            val to = from.shuffled(random)

            val made = moves(from, to)

            assertEquals(to, apply(from, made))
            assertTrue(made.size < from.size || from.size == 1)
        }
    }
}
