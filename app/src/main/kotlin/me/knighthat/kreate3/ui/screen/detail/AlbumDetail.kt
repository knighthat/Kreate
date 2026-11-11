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

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.MediaItem
import com.materialkolor.hct.Hct
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.database.model.ReleaseType
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.EXTRA_ARTIST_IDS
import me.knighthat.kreate3.ui.component.EXTRA_ARTIST_NAMES
import me.knighthat.kreate3.ui.component.ExplicitBadge
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LabeledIconToggle
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.MoreSheetButton
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueActionButton
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.component.albumPictureKey
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.previewCover
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberContentsAfterPicture
import me.knighthat.kreate3.ui.component.rememberSharedPicture
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.artistRoute
import me.knighthat.kreate3.ui.theme.rememberCoverSeed
import kotlin.math.roundToInt

/** Edge of the cover beside the name. */
private val TINTED_COVER_SIZE = 136.dp

/** Edge of the cover once the header shrank, beside the artists and the summary. */
private val TINTED_SMALL_COVER_SIZE = 56.dp

/** Room at the header's sides. */
private val TINTED_PADDING = 16.dp

/** Room between the cover and the texts beside it. */
private val TINTED_GAP = 16.dp

/** Height at the header's bottom where its shade fades out over the list. */
private val TINTED_FADE = 28.dp

/** How far above the header's bottom the list's rows start to show, fully there [LIST_FADE_BELOW] under it. */
private val LIST_FADE_ABOVE = 24.dp

/** How far under the header's bottom the list's rows are whole, see [LIST_FADE_ABOVE]. */
private val LIST_FADE_BELOW = 8.dp

/** How dark the header's color is, HCT tone: dark enough for white text on any cover. */
private const val TINT_TONE = 30.0

/** Text under the name on the tint, a little dimmer than the name. */
private const val TINTED_SECONDARY_ALPHA = 0.75f

private val TINTED_BADGE_SIZE = 16.dp

/**
 * The frame of an album's page: no picture behind anything, the header is filled with a dark
 * shade of the cover's main color instead, fading into the page at its bottom. The cover sits at
 * the start with the badges, the overline, name, [artists] (a tap opens them), year and [summary]
 * beside it, the [description] under them. The bar has the [action] button and the ⋮ sheet.
 *
 * The header stays at the top and the list starts under it, its rows going on up behind the
 * fade. Scrolling up first shrinks the header, the list going up with its bottom: the cover gets
 * small, the badges, overline, name and description fade out, until only the cover, artists, year
 * and [summary] are left under the bar, which shows the name with the badges under it. Only then
 * does the list scroll, even a list too short to scroll at all; scrolling down, the header grows
 * again once the list is back at its top.
 *
 * @param albumId whose page it is, the cover of its card grows into the header as it opens
 * @param standIn the cover the album was opened with, see [OpenedItem]: in memory already, it
 * shows until [models] load
 * @param tint the color the header is a shade of, the cover's own by default; the theme's primary
 * while there is none
 * @param description what the source says about the album, `null` when it says nothing
 * @param releaseType the kind of release the overline names, `null` is an album
 * @param onOpenArtist opens the album's artist, `null` when there is none to open
 * @param action what the button beside ⋮ does with the songs, `null` hides the button
 * @param onAction runs [action], `null` turns the button off while there are no songs
 * @param menu the sheet the ⋮ button opens, given what closes it; `null` hides the button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScaffold(
    name: String,
    albumId: String,
    models: List<Any>,
    artists: String?,
    year: Int?,
    summary: String?,
    origin: Origin?,
    isExplicit: Boolean,
    description: String?,
    onBack: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    modifier: Modifier = Modifier,
    standIn: Uri? = null,
    releaseType: ReleaseType? = null,
    tint: Color = rememberCoverSeed(models),
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    action: QueueAction? = null,
    onAction: ((QueueAction) -> Unit)? = null,
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val covers = remember(models, standIn) {
        (models + listOfNotNull(thumbnailModel(standIn, null))).distinct()
    }
    val picture = rememberSharedPicture(albumPictureKey(albumId), MaterialTheme.shapes.medium)
    // Everything but the cover, shown once it landed, see pictureFirst
    val contents = rememberContentsAfterPicture()
    val seed = tint.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    val shade by animateColorAsState(
        remember(seed) { Color(Hct.fromInt(seed.toArgb()).withTone(TINT_TONE).toInt()) },
        label = "album tint",
    )
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    // The header's heights, measured by it: the list starts under the whole of it
    var heights by remember { mutableStateOf(HeaderHeights(0, 0)) }
    // Pixels the header shrank by, read only while laying out and drawing
    var shrunk by rememberSaveable { mutableFloatStateOf(0f) }
    val range = (heights.expanded - heights.collapsed).toFloat().coerceAtLeast(0f)
    // A header that got shorter, like a description folded back, can't stay shrunk by more
    LaunchedEffect(range) { if (shrunk > range) shrunk = range }
    // How far the header shrank, 0 whole, 1 as small as it gets
    val collapse = { if (range > 0f) shrunk / range else 0f }
    val shrinking = remember(range) {
        object : NestedScrollConnection {
            // Up, the header shrinks before the list scrolls
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val taken = maxOf(available.y, shrunk - range)
                shrunk -= taken
                return Offset(0f, taken)
            }

            // Down, it grows with what the list, back at its top, left
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val taken = minOf(available.y, shrunk)
                shrunk -= taken
                return Offset(0f, taken)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        modifier = modifier,
    ) { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(shrinking)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding + PaddingValues(
                    start = insets.calculateStartPadding(direction),
                    top = with(density) { heights.expanded.toDp() },
                    end = insets.calculateEndPadding(direction),
                    bottom = insets.calculateBottomPadding(),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    // Up with the header's bottom, taller by as much so it still reaches the end
                    .layout { measurable, constraints ->
                        val up = shrunk.roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = constraints.maxHeight + up,
                                maxHeight = constraints.maxHeight + up,
                            )
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(0, -up)
                        }
                    }
                    .then(contents)
                    // Rows dissolve around the header's bottom instead of showing through its
                    // fade higher up. In the list's own coordinates, which the layout above moved
                    // up by what the header shrank by, that bottom stays where the padding put it
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val bottom = heights.expanded.toFloat()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black,
                                startY = bottom - LIST_FADE_ABOVE.toPx(),
                                endY = bottom + LIST_FADE_BELOW.toPx(),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                content = content,
            )

            TintedHeader(
                name = name,
                models = covers,
                cover = picture.modifier,
                artists = artists,
                year = year,
                summary = summary,
                origin = origin,
                isExplicit = isExplicit,
                description = description,
                releaseType = releaseType,
                shade = shade,
                topPadding = barHeight,
                collapse = collapse,
                onOpenArtist = onOpenArtist,
                onHeights = { heights = it },
                contents = contents,
                // Dragged, it scrolls like the list, so it shrinks the header the same way
                modifier = Modifier.scrollable(
                    listState,
                    Orientation.Vertical,
                    reverseDirection = true,
                ),
            )

            TintedTopBar(
                name = name,
                origin = origin,
                isExplicit = isExplicit,
                collapse = collapse,
                onBack = onBack,
                action = action,
                onAction = onAction,
                onMore = if (menu != null) ({ menuOpen = true }) else null,
                modifier = contents,
            )
        }
    }

    if (menuOpen && menu != null) menu { menuOpen = false }
}

/** Heights in pixels of the album header, whole and as small as it gets. */
private data class HeaderHeights(val expanded: Int, val collapsed: Int)

/**
 * The album header of [AlbumDetailScaffold], [collapse] of the way to its smallest. Its texts keep
 * the width they have beside the large cover, so it is as tall at each step however it shrank.
 *
 * @param cover the shared cover's modifier, the cover clips itself
 * @param contents fades everything but the cover in after it, see [rememberContentsAfterPicture]
 * @param onHeights gets the header's heights whenever they change
 */
@Composable
private fun TintedHeader(
    name: String,
    models: List<Any>,
    cover: Modifier,
    artists: String?,
    year: Int?,
    summary: String?,
    origin: Origin?,
    isExplicit: Boolean,
    description: String?,
    releaseType: ReleaseType?,
    shade: Color,
    topPadding: Dp,
    collapse: () -> Float,
    onOpenArtist: (() -> Unit)?,
    onHeights: (HeaderHeights) -> Unit,
    contents: Modifier,
    modifier: Modifier = Modifier,
) {
    val dim = Color.White.copy(alpha = TINTED_SECONDARY_ALPHA)
    val shape = MaterialTheme.shapes.medium
    var reported by remember { mutableStateOf<HeaderHeights?>(null) }

    Layout(
        contents = listOf(
            // The shade, filled to the header's height at each step, fading into the page, see
            // drawEasedFade
            {
                Box(contents.drawBehind { drawEasedFade(shade, reversed = true, soft = true) })
            },
            {
                Thumbnail(
                    models = models,
                    shape = shape,
                    modifier = cover.shadow(12.dp, shape),
                )
            },
            // What fades out as the header shrinks
            {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = contents,
                ) {
                    if (origin != null || isExplicit)
                        AlbumBadges(origin, isExplicit, dim, Modifier.padding(bottom = 2.dp))
                    Text(
                        (releaseType?.let { stringResource(it.labelRes) }
                            ?: pluralStringResource(R.plurals.unit_album, 1)).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = dim,
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            // What stays beside the small cover
            {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = contents,
                ) {
                    ArtistLine(artists, year, onOpenArtist, linkColor = Color.White, color = dim)
                    summary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = dim)
                    }
                }
            },
            {
                // Its own padding, its tap area reaches the edges
                description?.let {
                    DetailDescription(
                        it,
                        modifier = contents,
                        color = dim,
                        linkColor = Color.White,
                    )
                }
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (shadeBox, coverBox, fading, staying, describing), constraints ->
        val fraction = collapse()
        val width = constraints.maxWidth
        val padding = TINTED_PADDING.roundToPx()
        val gap = TINTED_GAP.roundToPx()
        val lineGap = 2.dp.roundToPx()
        val top = topPadding.roundToPx() + 4.dp.roundToPx()
        val fade = TINTED_FADE.roundToPx()
        val large = TINTED_COVER_SIZE.roundToPx()
        val small = TINTED_SMALL_COVER_SIZE.roundToPx()
        val textWidth = (width - 2 * padding - large - gap).coerceAtLeast(0)
        val text = Constraints(maxWidth = textWidth)

        val upper = fading.first().measure(text)
        val lower = staying.first().measure(text)
        val about = describing.firstOrNull()?.measure(Constraints(maxWidth = width))
        val aboutHeight = about?.let { it.height + 8.dp.roundToPx() } ?: 0

        val heights = HeaderHeights(
            expanded = top + maxOf(
                large,
                upper.height + lineGap + lower.height
            ) + aboutHeight + fade,
            collapsed = top + maxOf(small, lower.height) + fade,
        )
        if (heights != reported) {
            reported = heights
            onHeights(heights)
        }

        val coverSize = lerp(large, small, fraction)
        val upperHeight = (upper.height * (1 - fraction)).roundToInt()
        val rowHeight = maxOf(coverSize, upperHeight + lineGap + lower.height)
        val height = top + rowHeight + (aboutHeight * (1 - fraction)).roundToInt() + fade
        // Gone by half way, before what it covers takes its place
        val fadingAlpha = (1 - 2 * fraction).coerceIn(0f, 1f)
        val coverPlaceable = coverBox.first().measure(Constraints.fixed(coverSize, coverSize))
        val shadePlaceable = shadeBox.first().measure(Constraints.fixed(width, height))
        val textStart = padding + coverSize + gap
        val lowerTop = top + rowHeight - lower.height

        layout(width, height) {
            shadePlaceable.place(0, 0)
            coverPlaceable.place(padding, top + rowHeight - coverSize)
            upper.placeWithLayer(textStart, lowerTop - lineGap - upper.height) {
                alpha = fadingAlpha
            }
            lower.place(textStart, lowerTop)
            about?.placeWithLayer(0, top + rowHeight + 8.dp.roundToPx()) { alpha = fadingAlpha }
        }
    }
}

/** The origin and explicit badges of an album, in the header and under the bar's name. */
@Composable
private fun AlbumBadges(
    origin: Origin?,
    isExplicit: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
    origin?.let { OriginBadge(it, TINTED_BADGE_SIZE, tint = tint) }
    if (isExplicit) ExplicitBadge(TINTED_BADGE_SIZE)
}

/**
 * The bar of the tinted design, over the header's shade: the [name], with the badges under it,
 * fading in over the second half of [collapse].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TintedTopBar(
    name: String,
    origin: Origin?,
    isExplicit: Boolean,
    collapse: () -> Float,
    onBack: () -> Unit,
    action: QueueAction?,
    onAction: ((QueueAction) -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    TopAppBar(
        title = {
            Column(Modifier.graphicsLayer { alpha = (2 * collapse() - 1).coerceIn(0f, 1f) }) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (origin != null || isExplicit)
                    AlbumBadges(
                        origin,
                        isExplicit,
                        Color.White.copy(alpha = TINTED_SECONDARY_ALPHA)
                    )
            }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
            action?.let {
                QueueActionButton(it, { onAction?.invoke(it) }, enabled = onAction != null)
            }
            onMore?.let { MoreSheetButton(it) }
        },
        // The header's shade is behind it
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        modifier = modifier,
    )
}

/**
 * The page of the album's artist: of the artists credited on its [songs][this], the first that
 * the album names in [names], else the first credited on its first song. `null` without credits.
 */
internal fun List<MediaItem>.albumArtistRoute(names: List<String>?): Route? {
    val credits = flatMap { song ->
        val extras = song.mediaMetadata.extras
        val ids = extras?.getStringArray(EXTRA_ARTIST_IDS).orEmpty()
        val credited = extras?.getStringArray(EXTRA_ARTIST_NAMES).orEmpty()
        ids.mapIndexed { index, id -> Triple(song.origin, id, credited.getOrNull(index)) }
    }
    val artist = names.orEmpty().firstNotNullOfOrNull { name ->
        credits.firstOrNull { it.third == name }
    } ?: credits.firstOrNull()
    return artist?.let { (origin, id) -> artistRoute(origin, id) }
}

/**
 * The sheet of an album's ⋮ button: Play, Bookmark and Share as buttons, then Shuffle, Play next,
 * Add to queue, the artist, Statistics and, for an album that can be, Reorder as rows.
 *
 * @param onAction runs a [QueueAction] with the album's songs, `null` while there are none: the
 * buttons and rows of those are off
 * @param isBookmarked whether the album is bookmarked, `null` turns Bookmark off, while it isn't
 * known
 * @param share gives a link to the album, `null` turns Share off
 * @param onOpenArtist opens the album's artist, `null` hides the row
 * @param isReordering whether the songs are being moved, `null` hides Reorder
 * @param onResetOrder what a long press on Reorder does, asking to put back the album's order
 */
@Composable
fun AlbumMenu(
    name: String?,
    onDismiss: () -> Unit,
    onAction: ((QueueAction) -> Unit)?,
    isBookmarked: Boolean?,
    onToggleBookmark: () -> Unit,
    share: (suspend () -> Result<String>)?,
    onOpenArtist: (() -> Unit)?,
    onOpenStatistics: () -> Unit,
    onEdit: (() -> Unit)? = null,
    isReordering: Boolean? = null,
    canReorder: Boolean = true,
    onReorder: () -> Unit = {},
    onResetOrder: () -> Unit = {},
) {
    val linkShare = rememberLinkShare(name, ShareKind.ALBUM) {
        share?.invoke() ?: Result.failure(IllegalStateException("Nothing to share"))
    }

    ConfigSheet(
        onDismiss = onDismiss,
        actions = { close ->
            LabeledIconButton(
                icon = R.drawable.play_arrow,
                label = stringResource(R.string.menu_shortcut_play),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.PLAY) } }
            LabeledIconToggle(
                isSet = isBookmarked == true,
                enabled = isBookmarked != null,
                setIcon = R.drawable.bookmark_filled,
                unsetIcon = R.drawable.bookmark_outline,
                label = stringResource(R.string.menu_shortcut_bookmark),
                onClick = onToggleBookmark,
            )
            LabeledIconButton(
                icon = R.drawable.share,
                label = stringResource(R.string.menu_shortcut_share),
                enabled = share != null && linkShare.enabled,
                loading = linkShare.sharing,
                // A link of the server can take a moment, the sheet stays until it is there
            ) { linkShare.share { close {} } }
        },
        entries = { close ->
            ConfigEntry(
                R.drawable.shuffle,
                stringResource(R.string.menu_shuffle),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.SHUFFLE) } }
            ConfigEntry(
                R.drawable.queue_play_next,
                stringResource(R.string.menu_play_next),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.PLAY_NEXT) } }
            ConfigEntry(
                R.drawable.playlist_add,
                stringResource(R.string.menu_add_to_queue),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.ENQUEUE) } }
            if (onOpenArtist != null)
                ConfigEntry(
                    R.drawable.artist,
                    pluralStringResource(R.plurals.menu_go_to_artist, 1)
                        .replaceFirstChar { it.uppercase() },
                ) { close(onOpenArtist) }
            ConfigEntry(R.drawable.insert_chart, stringResource(R.string.menu_statistics)) {
                close(onOpenStatistics)
            }
            if (onEdit != null)
                ConfigEntry(R.drawable.edit, stringResource(R.string.menu_edit)) { close(onEdit) }
            if (isReordering != null)
                ConfigEntry(
                    icon = if (isReordering) R.drawable.lock_open else R.drawable.lock,
                    label = if (isReordering)
                        stringResource(R.string.semantic_lock_the_order)
                    else
                        stringResource(R.string.semantic_reorder),
                    // Stays on with fewer songs, so it can be turned off
                    enabled = isReordering || canReorder,
                    onLongClick = { close(onResetOrder) },
                ) { close(onReorder) }
        },
    )
}

@ScreenPreviews
@Composable
private fun AlbumDetailPreview() = PreviewTheme {
    val models = remember { listOf<Any>(previewCover()) }
    // Seven discs of three songs, long enough to scroll under the header
    val discs = previewSongs().let { all ->
        List(7) { if (it % 2 == 0) all.take(3) else all.drop(3) }
    }
    val songs = discs.flatten()
    val resources = LocalResources.current

    AlbumDetailScaffold(
        name = "Afterglow",
        albumId = "",
        models = models,
        artists = "Neon Coast",
        year = 2021,
        summary = resources.albumSummary(
            songs.size,
            songs.sumOf { it.mediaMetadata.durationMs ?: 0 }),
        origin = Origin.LOCAL,
        isExplicit = true,
        onBack = {},
        description = "Recorded over one summer on the coast, Afterglow follows a single evening " +
                "from the last light to the first stars, with synths that drift like the tide.",
        onOpenArtist = {},
        // What the cover gives, a static preview runs no effect to work it out
        tint = Color(0xFF8E3B6B),
        action = QueueAction.PLAY,
        onAction = {},
        menu = {},
    ) {
        discs.forEachIndexed { disc, discSongs ->
            item(key = "disc$disc") { DiscHeader(disc + 1) }
            itemsIndexed(discSongs, key = { _, it -> "$disc:${it.mediaId}" }) { index, song ->
                AlbumTrack(song, index + 1, onClick = {})
            }
        }
    }
}
