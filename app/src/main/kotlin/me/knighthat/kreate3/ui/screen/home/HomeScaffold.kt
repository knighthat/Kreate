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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.ConfigButton
import me.knighthat.kreate3.ui.component.FilterIconButton
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.ProfileButton
import me.knighthat.kreate3.ui.component.SearchButton
import me.knighthat.kreate3.ui.component.SettingsButton
import me.knighthat.kreate3.ui.component.icon
import me.knighthat.kreate3.ui.component.sourceRes
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
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    LargeTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_monochrome),
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
            ProfileButton(onProfile)
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
 * don't fit, then a button opening the tab's options that always stays at the end. A short line
 * parts the chips from it, unless [LocalIconOnly] shows the origins as icons.
 *
 * @param onConfigure opens the options, `null` for a tab without them
 */
@Composable
private fun HomeToolbar(
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    scroll: ToolbarScroll,
    onConfigure: (() -> Unit)?,
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
        val iconOnly = LocalIconOnly.current
        // The icons alone need no line
        val lined = !iconOnly && choices.isNotEmpty()
        val chipScroll = rememberScrollState()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                // Chips that don't fit fade out into the line, as if they went under it
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (lined && chipScroll.canScrollForward) {
                        val fade = CHIP_FADE.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Black, Color.Transparent),
                                startX = size.width - fade,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - fade, 0f),
                            size = Size(fade, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .horizontalScroll(chipScroll)
                // The room before the line scrolls with the chips, so a cut chip meets the line
                .padding(start = 16.dp, end = if (lined) 18.dp else 16.dp),
        ) {
            choices.forEach {
                if (iconOnly)
                    OriginToggle(it, it in selected) { onToggleOrigin(it) }
                else
                    FilterChip(
                        selected = it in selected,
                        onClick = { onToggleOrigin(it) },
                        label = { Text(stringResource(it.sourceRes)) },
                        leadingIcon = {
                            // The label says the same, nothing more for screen readers
                            Icon(
                                painter = painterResource(it.icon),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
            }
        }
        // Ends the chips, a bit taller than them
        if (lined) VerticalDivider(Modifier.height(FilterChipDefaults.Height + 8.dp))
        onConfigure?.let { ConfigButton(it) }
    }
}

/** How far the chips that don't fit fade out before the line ending them. */
private val CHIP_FADE = 24.dp

/**
 * The icon of [origin] in place of its chip, see [LocalIconOnly]: in the primary color while
 * [selected], in the disabled color otherwise. It can still be tapped either way.
 */
@Composable
private fun OriginToggle(origin: Origin, selected: Boolean, onToggle: () -> Unit) =
    FilterIconButton(origin.icon, stringResource(origin.sourceRes), selected, onToggle)

/**
 * The frame of a home tab: a large top bar named [title] that collapses when the scrollable
 * [content] is scrolled, over a row of origin chips and a button opening [sheet] that hides while
 * the list scrolls down.
 *
 * @param choices the origins to offer a chip for, see [OriginFilter.choices]
 * @param selected the origins the tab shows, every origin while empty
 * @param sheet the tab's options, a [me.knighthat.kreate3.ui.component.ConfigSheet] that calls
 * `onDismiss` once closed; `null` for no button
 * @param contentPadding room [content] adds around its items on top of the bars', e.g. so the
 * last one can scroll clear of what floats over the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    title: String,
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSearch: () -> Unit = {},
    onProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    sheet: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScroll = remember { ToolbarScroll() }
    var configuring by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                HomeTopBar(title, scrollBehavior, onSearch, onProfile, onSettings)
                HomeToolbar(
                    choices,
                    selected,
                    onToggleOrigin,
                    toolbarScroll,
                    sheet?.let { { configuring = true } },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        // The title collapses first, then the toolbar hides
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(toolbarScroll),
    ) { content(it + contentPadding) }

    if (configuring) sheet?.invoke { configuring = false }
}
