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

package me.knighthat.kreate3.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * One back stack per [Tab]. Switching tabs keeps the history of the tab you left, and going back
 * from any other tab lands on [Tab.SONGS] before the app closes.
 */
@Stable
class TopLevelBackStack internal constructor(
    private val stacks: Map<Tab, NavBackStack<NavKey>>,
    private val tab: MutableState<Tab>,
) {
    val currentTab: Tab get() = tab.value

    /** What to render: the start tab's stack, then the current tab's stack on top of it. */
    val backStack: List<NavKey>
        get() = if (currentTab == START) stacks.getValue(START)
        else stacks.getValue(START) + stacks.getValue(currentTab)

    fun switchTab(tab: Tab) {
        this.tab.value = tab
    }

    fun push(route: Route) {
        stacks.getValue(currentTab).add(route)
    }

    fun pop() {
        val stack = stacks.getValue(currentTab)
        if (stack.size > 1) stack.removeLastOrNull()
        else tab.value = START
    }

    companion object {
        val START = Tab.SONGS
    }
}

@Composable
fun rememberTopLevelBackStack(): TopLevelBackStack {
    val tab = rememberSaveable { mutableStateOf(TopLevelBackStack.START) }
    val stacks = Tab.entries.associateWith { rememberNavBackStack(it.route) }

    return remember(stacks) { TopLevelBackStack(stacks, tab) }
}
