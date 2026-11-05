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

package me.knighthat.kreate3.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Lets the user pull the scrollable [content] down from its top to ask [onRefresh] for fresh
 * data, with the spinner showing while [refreshing]. [content] stays where it is, only the spinner
 * comes down.
 *
 * The pull takes the scroll before anything inside [content] does, so a swipe up first takes the
 * pull back. A screen whose bars react to the same scroll applies [pullToRefresh] and
 * [RefreshIndicator] itself, with the pull outside those bars' nested scroll.
 *
 * @param enabled `false` for a pull that would do nothing right now, like on a local list
 * @param indicatorTop where the spinner comes down from, the bar's height for a screen whose
 * [content] scrolls under a bar; a plain top bar under the status bar by default
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorTop: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val gate = remember { PullGate() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(
                state = state,
                isRefreshing = refreshing,
                enabled = enabled && gate.open,
                onRefresh = onRefresh,
            )
            .nestedScroll(gate),
    ) {
        content()
        RefreshIndicator(state, refreshing, indicatorTop)
    }
}

/**
 * Keeps a pull to refresh for a swipe of its own: one that already scrolled the list (or what is
 * around it) is not a pull when it reaches the top and goes on, only a new swipe is. Put it in the
 * chain as `.pullToRefresh(enabled = gate.open).nestedScroll(gate)`, the pull outside it.
 *
 * A swipe that began a pull stays the pull's: swiping back up past where it began scrolls nothing.
 */
@Stable
class PullGate : NestedScrollConnection {

    /** Whether the swipe in progress may pull, `false` once it scrolled something. */
    var open by mutableStateOf(true)
        private set

    // Whether the swipe in progress was pulled down past the top, so it is a pull
    private var pulled = false

    // What is left of an upward swipe once the pull took its share, goes nowhere
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (pulled && source == NestedScrollSource.UserInput && available.y < 0f)
            Offset(0f, available.y)
        else
            Offset.Zero

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            if (consumed.y != 0f)
                open = false
            else if (open && available.y > 0f)
                pulled = true
        }
        return Offset.Zero
    }

    // The finger is up, the next swipe is a new one
    override suspend fun onPreFling(available: Velocity): Velocity {
        open = true
        pulled = false
        return Velocity.Zero
    }
}

/** The spinner of a pull to refresh tracked by [state], [top] below the top of its [BoxScope]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.RefreshIndicator(state: PullToRefreshState, refreshing: Boolean, top: Dp) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = refreshing,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = top),
    )
}
