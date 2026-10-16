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

package me.knighthat.kreate3.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.ui.component.ProfileButton
import me.knighthat.kreate3.ui.component.SearchButton
import me.knighthat.kreate3.ui.component.SettingsButton
import me.knighthat.kreate3.ui.component.label
import kotlin.math.roundToInt

/**
 * The title large under the logo and the actions, shrinking into one row as [content] scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    LargeTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                tint = IconButtonDefaults.iconButtonColors().contentColor,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = {

                        }
                    ),
            )
        },
        actions = {
            SearchButton(onSearch)
            ProfileButton()
            SettingsButton(onSettings)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceDim,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceDim,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Hides the toolbar as the list scrolls down, and shows it again as soon as it scrolls up. The
 * scroll it takes for that doesn't move the list.
 */
@Stable
private class ToolbarScroll : NestedScrollConnection {

    /** Height of the toolbar, known once it is measured. */
    var height = 0f

    /** How far the toolbar is pushed up, from `0` (shown) to -[height] (hidden). */
    var offset by mutableFloatStateOf(0f)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val before = offset
        offset = (offset + available.y).coerceIn(-height, 0f)
        return Offset(0f, offset - before)
    }
}

/**
 * One row under the title: a chip for each origin of [choices], scrolling sideways when they
 * don't fit, then [quickActions] that always stay at the end.
 */
@Composable
private fun HomeToolbar(
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    scroll: ToolbarScroll,
    quickActions: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                scroll.height = placeable.height.toFloat()
                val offset = scroll.offset.roundToInt()
                layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) {
                    placeable.place(0, offset)
                }
            }
            // The list scrolls under the bar
            .background(MaterialTheme.colorScheme.surfaceDim)
            .padding(end = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            choices.forEach {
                FilterChip(
                    selected = it in selected,
                    onClick = { onToggleOrigin(it) },
                    label = { Text(it.label) },
                )
            }
        }
        quickActions()
    }
}

/**
 * The frame of a home tab: a large top bar named [title] that collapses when the scrollable
 * [content] is scrolled, over a row of origin chips and [quickActions] that hides while the list
 * scrolls down.
 *
 * @param choices the origins to offer a chip for, see [OriginFilter.choices]
 * @param selected the origins the tab shows, every origin while empty
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    title: String,
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onSettings: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    quickActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScroll = remember { ToolbarScroll() }

    Scaffold(
        topBar = {
            Column {
                HomeTopBar(title, scrollBehavior, onSearch, onSettings)
                HomeToolbar(choices, selected, onToggleOrigin, toolbarScroll, quickActions)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        // The title collapses first, then the toolbar hides
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(toolbarScroll),
        content = content,
    )
}
