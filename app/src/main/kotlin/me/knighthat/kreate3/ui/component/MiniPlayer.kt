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

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.PlaybackState
import me.knighthat.kreate3.ui.screen.player.LocalPlayerSheet
import me.knighthat.kreate3.ui.screen.player.SETTLE_VELOCITY
import org.koin.compose.koinInject

private val BUTTON_SIZE = 40.dp

/**
 * The room the [MiniPlayer] takes at the bottom of a screen, measured by [PlaybackMiniPlayer].
 *
 * A screen keeps clear from outside only of the rest of its bottom bar, [outside], and ends its
 * lists with room for the mini player, [inside]. Its lists then run under the mini player and show
 * through while it's swiped away, instead of leaving an empty band where it was.
 */
@Stable
class MiniPlayerSpace {

    /**
     * How tall the mini player is, `0` while it doesn't show.
     */
    var height: Dp by mutableStateOf(0.dp)
        internal set

    /**
     * Room for the mini player, to add to the end of a list's content padding.
     */
    val inside: PaddingValues = object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
        override fun calculateTopPadding() = 0.dp
        override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
        override fun calculateBottomPadding() = height
    }

    /**
     * [padding] of a bar that holds the mini player, without the mini player.
     */
    fun outside(padding: PaddingValues): PaddingValues = object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
            padding.calculateLeftPadding(layoutDirection)

        override fun calculateTopPadding() = padding.calculateTopPadding()

        override fun calculateRightPadding(layoutDirection: LayoutDirection) =
            padding.calculateRightPadding(layoutDirection)

        override fun calculateBottomPadding() =
            (padding.calculateBottomPadding() - height).coerceAtLeast(0.dp)
    }
}

@Composable
fun rememberMiniPlayerSpace(): MiniPlayerSpace = remember { MiniPlayerSpace() }

/**
 * Whether the [MiniPlayer] shows: while the queue has songs and hasn't played to its end.
 */
val PlaybackState.hasMiniPlayer: Boolean
    get() = current != null && !isEnded

/**
 * The [MiniPlayer] for what [controller] is playing, hidden as [hasMiniPlayer] says. A tap
 * opens the full player of [LocalPlayerSheet], dragging it up pulls the player along, and it
 * fades as the player covers it.
 *
 * Dragged down, it follows the finger and fades out. Let go past half its height, or flung down,
 * it stops the player ([PlaybackController.stop]), otherwise it comes back.
 *
 * @param space measured as it shows, hides, and leaves the screen
 */
@Composable
fun PlaybackMiniPlayer(
    space: MiniPlayerSpace,
    modifier: Modifier = Modifier,
    controller: PlaybackController = koinInject(),
) {
    val density = LocalDensity.current
    DisposableEffect(space) { onDispose { space.height = 0.dp } }
    // Empty while nothing plays, it measures 0 then
    Box(modifier.onSizeChanged { space.height = with(density) { it.height.toDp() } }) {
        PlaybackMiniPlayer(controller = controller)
    }
}

@Composable
private fun PlaybackMiniPlayer(controller: PlaybackController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val item = state.current?.takeIf { state.hasMiniPlayer } ?: return
    val sheet = LocalPlayerSheet.current
    val scope = rememberCoroutineScope()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current

    // Re-read every frame while playing, drawing is the only place that depends on it
    var progress by remember { mutableFloatStateOf(controller.progress()) }
    LaunchedEffect(item, state.isPlaying) {
        progress = controller.progress()
        while (state.isPlaying) {
            withFrameNanos { }
            progress = controller.progress()
        }
    }

    // How far it is dragged down, in pixels
    var pulled by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val threshold = with(LocalDensity.current) { SETTLE_VELOCITY.toPx() }
    val drag = rememberDraggableState { delta ->
        // Down from a closed player, or back up while pulled down
        if (pulled > 0f || (delta > 0f && (sheet?.fraction ?: 0f) == 0f)) {
            val next = (pulled + delta).coerceIn(0f, height.toFloat())
            // What's left of a drag up past the start opens the player
            if (next == 0f) sheet?.dragBy(delta + pulled)
            pulled = next
        } else
            sheet?.dragBy(delta)
    }

    MiniPlayer(
        item = item,
        isPlaying = state.isPlaying,
        progress = { progress },
        onTogglePlay = controller::togglePlay,
        onNext = controller::next,
        onClick = { sheet?.let { scope.launch { it.expand() } } },
        modifier = Modifier
            .onSizeChanged { height = it.height }
            .graphicsLayer {
                translationY = pulled
                val gone = if (height > 0) pulled / height else 0f
                alpha = (1f - (sheet?.fraction ?: 0f)) * (1f - gone)
            }
            .draggable(
                state = drag,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (pulled == 0f) return@draggable sheet?.settle(velocity, threshold) ?: Unit

                    val stop = velocity > threshold || pulled > height / 2f
                    animate(pulled, if (stop) height.toFloat() else 0f, velocity) { value, _ ->
                        pulled = value
                    }
                    // Hidden by now, it goes away once the queue is empty
                    if (stop) controller.stopUndoable(resources, notifier)
                },
            ),
        thumbnails = rememberSongThumbnails(item),
    )
}

/**
 * The song that is playing as a row, like a [SongItem] with the play and skip buttons at the
 * end, and a thin progress bar along the top.
 *
 * It only shows what it is given and reports taps, so whoever places it owns the playback state.
 *
 * @param progress how far into the song, from `0` to `1`. It is read while drawing, so a value
 * that changes every moment doesn't redo the layout.
 * @param thumbnails pictures to try for the cover, see [Thumbnail], the item's own by default
 */
@Composable
fun MiniPlayer(
    item: MediaItem,
    isPlaying: Boolean,
    progress: () -> Float,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnails: List<Any>? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                drawStopIndicator = {}
            )
            ItemRow(
                item = item,
                onClick = onClick,
                thumbnails = thumbnails,
                trailing = {
                    Row {
                        PlayPauseButton(isPlaying, onTogglePlay, Modifier.size(BUTTON_SIZE))
                        SkipNextButton(onNext, Modifier.size(BUTTON_SIZE))
                    }
                },
            )
        }
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun MiniPlayerPreview() = PreviewTheme {
    val songs = previewSongs()

    Column(modifier = Modifier.width(360.dp)) {
        MiniPlayer(
            item = songs[2],
            isPlaying = true,
            progress = { 0.4f },
            onTogglePlay = {},
            onNext = {},
            onClick = {},
        )
        MiniPlayer(
            item = songs[1],
            isPlaying = false,
            progress = { 0.8f },
            onTogglePlay = {},
            onNext = {},
            onClick = {},
        )
    }
}
//</editor-fold>
