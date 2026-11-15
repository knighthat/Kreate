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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.QueueActionButton
import me.knighthat.kreate3.ui.component.TooltipSide

/** Narrowest page that puts its header beside its list, when it is also wider than tall. */
private val SIDE_BY_SIDE_MIN_WIDTH = 600.dp

/** Share of the page's width the header takes beside the list. */
private const val HEADER_PANE_FRACTION = 0.4f

private val HEADER_PANE_MIN_WIDTH = 280.dp

/** Room on each side of what the header pane holds. */
internal val HEADER_PANE_PADDING = 24.dp

/** Share of the pane's height a cover takes at most, the rest is for the texts under it. */
private const val HEADER_PANE_COVER_HEIGHT = 0.45f

/**
 * Whether a detail page of [width] × [height] puts its header in a pane at the start and its list
 * beside it: at least [SIDE_BY_SIDE_MIN_WIDTH] wide and wider than tall, like a tablet or a phone
 * on its side. Narrower or upright pages keep the header over the list.
 */
internal fun isSideBySide(width: Dp, height: Dp): Boolean =
    width >= SIDE_BY_SIDE_MIN_WIDTH && width > height

/** The header pane's width on a page [width] wide. */
internal fun headerPaneWidth(width: Dp): Dp =
    (width * HEADER_PANE_FRACTION).coerceAtLeast(HEADER_PANE_MIN_WIDTH)

/** The side of a square cover in a header pane [width] × [height]. */
internal fun headerPaneCover(width: Dp, height: Dp): Dp =
    min(width - HEADER_PANE_PADDING * 2, height * HEADER_PANE_COVER_HEIGHT)

/**
 * A detail page side by side: the [header] pane [paneWidth] wide at the start, the [list] side
 * filling the rest.
 */
@Composable
internal fun SidePanes(
    paneWidth: Dp,
    header: @Composable BoxScope.() -> Unit,
    list: @Composable ColumnScope.() -> Unit,
) = Row(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .width(paneWidth)
            .fillMaxHeight(),
        content = header,
    )
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight(),
        content = list,
    )
}

/**
 * What a header pane shows under its bar, centered in the pane's height and scrolling when taller,
 * like on a phone on its side. It scrolls under the bar, never behind it.
 *
 * @param padding room around it: its top is the bar over it, the rest the system bars and what
 * floats at the bottom
 */
@Composable
internal fun HeaderPaneColumn(
    paneHeight: Dp,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val top = padding.calculateTopPadding()

    Box(
        modifier
            .fillMaxSize()
            .padding(top = top)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = paneHeight - top)
                .padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    bottom = padding.calculateBottomPadding(),
                ),
            content = content,
        )
    }
}

/**
 * The back button at the top of a header pane, with a [title] when the page names itself there,
 * both [color] on what the pane is filled with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeaderPaneBar(
    onBack: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = {},
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    TopAppBar(
        title = title,
        navigationIcon = { BackButton(onBack) },
        windowInsets = TopAppBarDefaults.windowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Start),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = color,
            titleContentColor = color,
        ),
        modifier = modifier,
    )
}

/** The bar over the list of a side-by-side page: the page's [actions], the header names it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListPaneBar(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    TopAppBar(
        title = {},
        actions = actions,
        windowInsets = TopAppBarDefaults.windowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.End),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceDim,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceDim,
        ),
        modifier = modifier,
    )
}

/**
 * Whether the page the ⋮ sheet is opened from has the queue's actions as buttons of its bar, so
 * the sheet leaves them out: on a wide page, see [ListPaneQueueButtons].
 */
internal val LocalQueueButtonsInBar = staticCompositionLocalOf { false }

/** The queue's actions as buttons of the bar over a wide page's list, `null` turns them off. */
@Composable
internal fun RowScope.ListPaneQueueButtons(onAction: ((QueueAction) -> Unit)?) =
    listOf(QueueAction.PLAY, QueueAction.SHUFFLE, QueueAction.PLAY_NEXT, QueueAction.ENQUEUE)
        .forEach { action ->
            QueueActionButton(action, { onAction?.invoke(action) }, enabled = onAction != null)
        }

/** Edge of the source's icon under the name in a header pane's bar. */
private val PANE_TITLE_BADGE_SIZE = 16.dp

/**
 * The page's [name] in its header pane's bar, with its [origin]'s icon and [details] under it,
 * like `🎵 · EP · 3 songs · 12 minutes`.
 *
 * @param color of the line under the name and the icon
 */
@Composable
internal fun HeaderPaneTitle(
    name: String,
    origin: Origin?,
    details: String,
    color: Color,
) = Column {
    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        origin?.let { OriginBadge(it, PANE_TITLE_BADGE_SIZE, tint = color) }
        Text(
            if (origin != null) "· $details" else details,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
