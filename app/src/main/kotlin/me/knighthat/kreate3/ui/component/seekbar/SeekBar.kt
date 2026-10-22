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

package me.knighthat.kreate3.ui.component.seekbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.PlaybackState
import me.knighthat.kreate3.playback.SleepTimer
import me.knighthat.kreate3.playback.SleepTimerAction
import me.knighthat.kreate3.ui.component.ComponentPreviews
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.formatDuration
import me.knighthat.kreate3.ui.component.previewItem
import me.knighthat.kreate3.ui.component.previewSongs
import kotlin.time.Duration


/**
 * Where the song is, with its elapsed, remaining and whole time under the bar. Dragging the bar
 * moves the time shown, the song moves once it is let go. The length the song says it has stands
 * in while the player doesn't know it yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(item: MediaItem, isPlaying: Boolean, controller: PlaybackController) {
    var position by remember { mutableLongStateOf(controller.position()) }
    var duration by remember { mutableStateOf(controller.duration()) }
    var dragged by remember { mutableStateOf<Float?>(null) }

    // Re-read every frame while playing, once when it pauses or the song changes
    LaunchedEffect(item, isPlaying) {
        while (true) {
            position = controller.position()
            duration = controller.duration()
            if (!isPlaying) break
            withFrameNanos { }
        }
    }

    val total = duration ?: item.mediaMetadata.durationMs
    val shown = dragged?.let { fraction -> total?.let { (fraction * it).toLong() } } ?: position

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = dragged ?: total?.let { (position.toFloat() / it).coerceIn(0f, 1f) } ?: 0f,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                val fraction = dragged
                if (fraction != null && total != null) {
                    val target = (fraction * total).toLong()
                    controller.seekTo(target)
                    position = target
                }
                dragged = null
            },
            enabled = total != null,
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(
                            color = SliderDefaults.colors().activeTrackColor,
                            shape = CircleShape
                        )
                )
            },
            track = { sliderState ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            color = SliderDefaults.colors().inactiveTrackColor,
                            shape = CircleShape
                        )
                )

                SliderDefaults.Track(
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = Color.Transparent,
                    ),
                    enabled = total != null,
                    sliderState = sliderState,
                    drawStopIndicator = {},
                    thumbTrackGapSize = Dp.Hairline,
                )
            }
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatDuration(shown) ?: "0:00", style = style, color = color)
            Text(
                text = total?.let { formatDuration(it - shown)?.let { left -> "-$left" } } ?: "",
                style = style,
                color = color,
            )
            Text(formatDuration(total) ?: "--:--", style = style, color = color)
        }
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
/** Stands still at [positionMs] of [durationMs], seeking only moves it. */
private class PreviewController(
    private var positionMs: Long,
    private val durationMs: Long?,
) : PlaybackController {

    override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())
    override val queue: StateFlow<List<MediaItem>> = MutableStateFlow(emptyList())
    override val shuffledOrder: StateFlow<List<Int>?> = MutableStateFlow(null)
    override val sleepTimer: StateFlow<SleepTimer?> = MutableStateFlow(null)

    override fun play(item: MediaItem) = Unit
    override fun playAll(items: List<MediaItem>) = Unit
    override fun radio(seed: MediaItem, append: Boolean) = Unit
    override fun enqueue(items: List<MediaItem>) = Unit
    override fun follow(seed: MediaItem, items: List<MediaItem>) = Unit
    override fun playNext(items: List<MediaItem>) = Unit
    override fun progress(): Float = durationMs?.let { positionMs.toFloat() / it } ?: 0f
    override fun togglePlay() = Unit
    override fun position(): Long = positionMs
    override fun duration(): Long? = durationMs
    override fun next() = Unit
    override fun drop(mediaId: String) = Unit
    override fun remove(index: Int) = Unit
    override fun move(from: Int, to: Int) = Unit
    override fun skipTo(index: Int) = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
    }

    override fun toggleShuffle() = Unit
    override fun cycleRepeat() = Unit
    override fun stop() = Unit
    override fun startSleepTimer(duration: Duration?) = Unit
    override fun changeSleepTimer(action: SleepTimerAction) = Unit
    override fun audioSessionId(): Int? = null
}

@ComponentPreviews
@Composable
private fun SeekBarHalfFilled() = PreviewTheme {
    val song = previewSongs().first()
    SeekBar(song, isPlaying = false, controller = PreviewController(82_000, 214_000))
}

@ComponentPreviews
@Composable
private fun SeekBarEmpty() = PreviewTheme {
    val song = previewSongs()[1]
    SeekBar(song, isPlaying = false, controller = PreviewController(0, null))
}

@ComponentPreviews
@Composable
private fun SeekBarDisabled() = PreviewTheme {
    SeekBar(
        item = previewItem("Untimed", "Unknown"),
        isPlaying = false,
        controller = PreviewController(0, null),
    )
}

@ComponentPreviews
@Composable
private fun SeekBarTenth() = PreviewTheme {
    val song = previewSongs()[1]
    SeekBar(song, isPlaying = false, controller = PreviewController(5000, null))
}
//</editor-fold>
