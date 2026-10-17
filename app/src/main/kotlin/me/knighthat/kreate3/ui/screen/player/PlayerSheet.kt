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

package me.knighthat.kreate3.ui.screen.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.ui.component.CollapseButton
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.hasMiniPlayer
import me.knighthat.kreate3.ui.component.memoryCacheKey
import me.knighthat.kreate3.ui.component.rememberSongThumbnails
import me.knighthat.kreate3.ui.component.seekbar.SeekBar
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much the player shrinks at the end of a back gesture, before it collapses. */
private const val BACK_SCALE = 0.1f

/** Speed per second above which a drag let go goes where it heads, like `anchoredDraggable`. */
private val SETTLE_VELOCITY = 125.dp

/** Share of the player's height the cover covers, from the top. */
private const val COVER_HEIGHT = 0.6f

/**
 * Share of the cover's height, from its bottom, that blurs more and more and fades out into the
 * blurred background on the way down.
 */
private const val COVER_FADE = 0.4f

/** Share of the player's height where the blurred background starts to fade into the color. */
private const val BACKGROUND_FADE_START = 0.6f

/** Stops of the eased gradients, more of them is smoother. */
private const val EASED_STOPS = 12

/**
 * How blurred a copy of the cover is: the edge in pixels it is loaded at and the radius of the
 * real blur over it. Stretched to the window, few pixels blur it on every Android version, the
 * real blur only smooths it where there is one.
 */
private data class Blur(val sampleSize: Int, val radius: Dp)

/** The copy behind the whole player. */
private val HEAVY_BLUR = Blur(sampleSize = 16, radius = 64.dp)

/** The copy over the bottom of the cover. */
private val COVER_BLUR = Blur(sampleSize = 24, radius = 40.dp)

/** The shade at the top of the cover, behind the collapse button and the logo. */
private val TOP_SHADE_HEIGHT = 120.dp
private const val TOP_SHADE_ALPHA = 0.6f

/** The row with the collapse button and the tabs, under the status bar. */
private val TOP_BAR_HEIGHT = 56.dp
private val COLLAPSE_BUTTON_SIZE = 48.dp

/**
 * How much the color covers the blurred cover behind the lyrics and the queue, so their text
 * reads on any cover.
 */
private const val PAGE_SCRIM_ALPHA = 0.6f

/** Pages of the full player, in the order of its tabs: the player between the other two. */
private enum class PlayerTab(val label: String) {
    LYRICS("Lyrics"),
    PLAYER("Player"),
    QUEUE("Queue"),
}

/** Where the full player stands. */
enum class PlayerSheetValue {
    /** Below the window, only the mini player shows. */
    COLLAPSED,

    /** Covering the window. */
    EXPANDED,
}

/**
 * Where the full player stands, see [PlayerSheet]. It moves between [PlayerSheetValue]s with the
 * finger, dragged up from the mini player ([playerSheetHandle]) or down from the player.
 */
@Stable
class PlayerSheetState(initialValue: PlayerSheetValue) {

    internal val draggable = AnchoredDraggableState(initialValue)

    /**
     * How far the player is open, `0` below the window to `1` covering it. Read it while
     * drawing where possible, it changes every frame of a drag.
     */
    val fraction: Float
        get() {
            val hidden = draggable.anchors.positionOf(PlayerSheetValue.COLLAPSED)
            val offset = draggable.offset
            if (hidden.isNaN() || hidden <= 0f || offset.isNaN())
                return if (draggable.currentValue == PlayerSheetValue.EXPANDED) 1f else 0f
            return (1f - offset / hidden).coerceIn(0f, 1f)
        }

    /** Whether the player is open or opening, going back collapses it then. */
    val isExpanded: Boolean
        get() = draggable.targetValue == PlayerSheetValue.EXPANDED

    suspend fun expand() = draggable.animateTo(PlayerSheetValue.EXPANDED)

    suspend fun collapse() = draggable.animateTo(PlayerSheetValue.COLLAPSED)

    /** Whether the player is off its open place, like halfway dragged down. */
    internal val isMoved: Boolean
        get() = draggable.offset.let { !it.isNaN() && it > 0f }

    /**
     * Goes where a drag let go at [velocity], in pixels per second, sends it: closed or open when
     * faster than [threshold] down or up, otherwise the nearest of the two.
     */
    internal suspend fun settle(velocity: Float, threshold: Float) = draggable.animateTo(
        when {
            velocity > threshold -> PlayerSheetValue.COLLAPSED
            velocity < -threshold -> PlayerSheetValue.EXPANDED
            fraction < 0.5f -> PlayerSheetValue.COLLAPSED
            else -> PlayerSheetValue.EXPANDED
        }
    )

    companion object {

        /** Keeps whether the player was open, like across a rotation. */
        val Saver: Saver<PlayerSheetState, PlayerSheetValue> = Saver(
            save = { it.draggable.settledValue },
            restore = { PlayerSheetState(it) },
        )
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState =
    rememberSaveable(saver = PlayerSheetState.Saver) {
        PlayerSheetState(PlayerSheetValue.COLLAPSED)
    }

/** The player of the app, for the mini players to open it. `null` where there is none. */
val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetState?> { null }

/**
 * Dragging this up opens the player of [state], it follows the finger. Nothing happens without
 * a player.
 */
fun Modifier.playerSheetHandle(state: PlayerSheetState?): Modifier =
    if (state == null) this else anchoredDraggable(state.draggable, Orientation.Vertical)

/**
 * Lets a list in the player drag it down once the list is at its top, and back up before the
 * list scrolls again. Only the finger moves the player, a fling of the list never does.
 *
 * @param threshold speed in pixels per second above which a drag let go goes where it heads
 */
private class PlayerSheetScroll(
    private val state: PlayerSheetState,
    private val threshold: Float,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || !state.isMoved)
            return Offset.Zero
        return Offset(0f, state.draggable.dispatchRawDelta(available.y))
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        return Offset(0f, state.draggable.dispatchRawDelta(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!state.isMoved) return Velocity.Zero
        state.settle(available.y, threshold)
        return available
    }
}

/**
 * The full player, drawn over everything else in the window. Collapsed it waits below the
 * window, the mini player drags it up or opens it with a tap ([LocalPlayerSheet]). Dragging it
 * down, its collapse button and going back close it, the back gesture shrinks it as it goes.
 * It closes by itself when there is nothing to play anymore.
 */
@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    modifier: Modifier = Modifier,
    controller: PlaybackController = koinInject(),
) {
    val playback by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isOpen by remember(state) { derivedStateOf { state.fraction > 0f } }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val listScroll = remember(state, density) {
        PlayerSheetScroll(state, with(density) { SETTLE_VELOCITY.toPx() })
    }

    LaunchedEffect(playback.hasMiniPlayer) {
        if (!playback.hasMiniPlayer && state.draggable.currentValue != PlayerSheetValue.COLLAPSED)
            state.collapse()
    }

    // Only composed while open: a handler added last goes first, the screens below were added
    // before it then. It leaves as the player collapses, so the collapse runs outside of it.
    if (state.isExpanded)
        PredictiveBackHandler { progress ->
            try {
                progress.collect { backProgress = it.progress }
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
            scope.launch {
                state.collapse()
                backProgress = 0f
            }
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                state.draggable.updateAnchors(
                    DraggableAnchors {
                        PlayerSheetValue.COLLAPSED at size.height.toFloat()
                        PlayerSheetValue.EXPANDED at 0f
                    }
                )
            },
    ) {
        val item = playback.current
        if (isOpen && item != null)
            FullPlayer(
                item = item,
                index = playback.index,
                isPlaying = playback.isPlaying,
                controller = controller,
                onCollapse = { scope.launch { state.collapse() } },
                modifier = Modifier
                    .offset { IntOffset(0, state.draggable.offset.roundToInt()) }
                    .graphicsLayer {
                        val scale = 1f - backProgress * BACK_SCALE
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .nestedScroll(listScroll)
                    .anchoredDraggable(state.draggable, Orientation.Vertical),
            )
    }
}

/**
 * The full player: [PlayerTab.PLAYER] with the song playing in the middle, its lyrics on the
 * left and the queue on the right, as pages to swipe between or pick with the tabs at the top,
 * beside the collapse button. It always opens on the player. The song's cover blurred stays
 * behind every page, more covered by the color away from the player so the text reads.
 *
 * @param index where [item] is in the queue, `-1` when unknown
 */
@Composable
private fun FullPlayer(
    item: MediaItem,
    index: Int,
    isPlaying: Boolean,
    controller: PlaybackController,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surface
    val covers = rememberSongThumbnails(item, HEADER_ARTWORK)
    // Made again each time the player opens, the player is composed only while open
    val pagerState = rememberPagerState(initialPage = PlayerTab.PLAYER.ordinal) {
        PlayerTab.entries.size
    }
    val scope = rememberCoroutineScope()
    // Under the tabs and above the navigation bar, the lists scroll behind both
    val pagePadding = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TOP_BAR_HEIGHT,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )

    Surface(color = background, modifier = modifier.fillMaxSize()) {
        // Not straight in the surface, which would stretch every layer to its whole size
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val coverHeight = maxHeight * COVER_HEIGHT

            // From top to bottom, the sides are cut
            BlurredCover(covers, HEAVY_BLUR, Modifier.matchParentSize())
            // Its lower part fades into the color, where the buttons are
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            *easedStops(BACKGROUND_FADE_START, rising = true, color = background)
                        )
                    ),
            )
            // Read while drawing, it follows the finger between the pages
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val page = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val away = abs(page - PlayerTab.PLAYER.ordinal).coerceAtMost(1f)
                        drawRect(background.copy(alpha = PAGE_SCRIM_ALPHA * away))
                    },
            )

            HorizontalPager(state = pagerState, modifier = Modifier.matchParentSize()) { page ->
                when (PlayerTab.entries[page]) {
                    PlayerTab.PLAYER -> NowPlayingPage(
                        item = item,
                        covers = covers,
                        isPlaying = isPlaying,
                        controller = controller,
                        coverHeight = coverHeight,
                    )

                    PlayerTab.LYRICS -> LyricsPage(item, pagePadding)
                    PlayerTab.QUEUE -> QueuePage(controller, index, pagePadding)
                }
            }

            // Keeps the tabs readable on a light cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOP_SHADE_HEIGHT)
                    .background(
                        Brush.verticalGradient(
                            listOf(background.copy(alpha = TOP_SHADE_ALPHA), Color.Transparent)
                        )
                    ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(TOP_BAR_HEIGHT)
                    .padding(horizontal = 8.dp),
            ) {
                CollapseButton(onCollapse)
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    divider = {},
                    modifier = Modifier.weight(1f),
                ) {
                    PlayerTab.entries.forEach { tab ->
                        Tab(
                            selected = pagerState.currentPage == tab.ordinal,
                            onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                            text = { Text(tab.label) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // As wide as the collapse button, the tabs stay in the middle
                Spacer(Modifier.width(COLLAPSE_BUTTON_SIZE))
            }
        }
    }
}

/**
 * The song playing, large: its cover across the top [COVER_HEIGHT] of the player, fading out
 * into the same cover blurred behind the whole player; under it its name and where it is with a
 * bar to move in it, and the buttons to go back, play or pause and skip near the bottom.
 */
@Composable
private fun NowPlayingPage(
    item: MediaItem,
    covers: List<Any>,
    isPlaying: Boolean,
    controller: PlaybackController,
    coverHeight: Dp,
) {
    val metadata = item.mediaMetadata

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                // Its bottom turns see-through, so it dissolves into the blurred cover
                .mask { Brush.verticalGradient(*easedStops(1f - COVER_FADE, rising = false)) },
        ) {
            // Fills the height, the sides are cut
            Thumbnail(
                models = covers,
                shape = RectangleShape,
                modifier = Modifier.matchParentSize(),
            )
            // The same cover blurred over it, more and more of it as the cover fades out
            BlurredCover(
                models = covers,
                blur = COVER_BLUR,
                modifier = Modifier
                    .matchParentSize()
                    .mask {
                        Brush.verticalGradient(*easedStops(1f - COVER_FADE, rising = true))
                    },
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = coverHeight)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = metadata.title?.toString().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (metadata.artist ?: metadata.subtitle)?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            SeekBar(item, isPlaying, controller)

            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ControlButton(R.drawable.skip_previous, "Previous", controller::previous)
                ControlButton(
                    icon = if (isPlaying) R.drawable.pause else R.drawable.play_arrow,
                    description = if (isPlaying) "Pause" else "Play",
                    onClick = controller::togglePlay,
                    iconSize = 56.dp,
                )
                ControlButton(R.drawable.skip_next, "Next", controller::next)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * Stops of a gradient of [color] that stays as it is up to [from] and then eases to its end:
 * from see-through to [opacity] when [rising], from [opacity] to see-through otherwise. A
 * straight gradient shows where it starts and ends, an eased one doesn't.
 */
private fun easedStops(
    from: Float,
    rising: Boolean,
    color: Color = Color.Black,
    opacity: Float = 1f,
): Array<Pair<Float, Color>> {
    val eased = List(EASED_STOPS + 1) { i ->
        val t = i.toFloat() / EASED_STOPS
        // Smoothstep: slow at both ends
        val amount = t * t * (3f - 2f * t)
        val alpha = opacity * if (rising) amount else 1f - amount
        (from + (1f - from) * t) to color.copy(alpha = alpha)
    }
    return (listOf(0f to eased.first().second) + eased).toTypedArray()
}

/**
 * Keeps what this draws as much as [brush] is opaque, see-through where it is see-through. The
 * brush is made for the size drawn.
 */
private fun Modifier.mask(brush: DrawScope.() -> Brush): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush = brush(), blendMode = BlendMode.DstIn)
        }

/**
 * The cover blurred as much as [blur] says, filling [modifier] with its sides cut. It tries
 * [models] in order until one loads.
 */
@Composable
private fun BlurredCover(models: List<Any>, blur: Blur, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(models) { mutableIntStateOf(0) }
    val model = models.getOrNull(failed) ?: return
    val request = remember(model, blur.sampleSize) {
        ImageRequest.Builder(context)
            .data(model)
            .size(blur.sampleSize)
            // A key of its own, a tiny picture must not stand in for a sharper one
            .memoryCacheKey(memoryCacheKey(model)?.let { "blurred:${blur.sampleSize}:$it" })
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed++ },
        modifier = modifier.blur(blur.radius),
    )
}

/** A large button of the player, the icon alone without anything around it. */
@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    iconSize: Dp = 40.dp,
) = IconButton(onClick = onClick, modifier = Modifier.size(iconSize + 24.dp)) {
    Icon(painterResource(icon), contentDescription = description, Modifier.size(iconSize))
}
