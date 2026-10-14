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

package me.knighthat.kreate.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelBackStackTest {

    private val stack = TopLevelBackStack(
        Tab.entries.associateWith { NavBackStack<NavKey>(it.route) },
        mutableStateOf(TopLevelBackStack.START),
    )

    @Test
    fun theStartTabShowsOnlyItself() {
        assertEquals(Tab.SONGS, stack.currentTab)
        assertEquals(listOf(Songs), stack.backStack)
    }

    @Test
    fun anotherTabSitsOnTopOfTheStartTab() {
        stack.switchTab(Tab.ALBUMS)
        stack.push(LocalAlbum("a1"))

        assertEquals(listOf(Songs, Albums, LocalAlbum("a1")), stack.backStack)
    }

    @Test
    fun popGoesBackInsideTheTab() {
        stack.switchTab(Tab.ARTISTS)
        stack.push(LocalArtist("r1"))

        stack.pop()

        assertEquals(Tab.ARTISTS, stack.currentTab)
        assertEquals(listOf(Songs, Artists), stack.backStack)
    }

    @Test
    fun popAtTheRootOfATabReturnsToTheStartTab() {
        stack.switchTab(Tab.PLAYLISTS)

        stack.pop()

        assertEquals(Tab.SONGS, stack.currentTab)
        assertEquals(listOf(Songs), stack.backStack)
    }

    @Test
    fun aTabKeepsItsHistoryWhileAnotherIsShown() {
        stack.switchTab(Tab.ALBUMS)
        stack.push(LocalAlbum("a1"))
        stack.switchTab(Tab.ARTISTS)

        stack.switchTab(Tab.ALBUMS)

        assertEquals(listOf(Songs, Albums, LocalAlbum("a1")), stack.backStack)
    }

    @Test
    fun popAtTheRootOfTheStartTabStays() {
        stack.pop()

        assertEquals(Tab.SONGS, stack.currentTab)
        assertEquals(listOf(Songs), stack.backStack)
    }

    @Test
    fun theStartTabHasItsOwnHistory() {
        stack.push(Search)
        stack.switchTab(Tab.ALBUMS)

        assertEquals(listOf(Songs, Search, Albums), stack.backStack)
    }
}
