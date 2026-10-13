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

package me.knighthat.kreate.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.knighthat.kreate.constant.ContrastLevel
import me.knighthat.kreate.constant.ThemeMode
import me.knighthat.kreate.ui.theme.KreateTheme

private val BUTTON_SIZE = 40.dp

/**
 * The song that is playing as a row, like a [SongItem] with the play and skip buttons at the
 * end, and a thin progress bar along the top.
 *
 * It only shows what it is given and reports taps, so whoever places it owns the playback state.
 *
 * @param progress how far into the song, from `0` to `1`. It is read while drawing, so a value
 * that changes every moment doesn't redo the layout.
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

@Composable
private fun MiniPlayerSample(isPlaying: Boolean) {
    MiniPlayer(
        item = MediaItem.Builder()
            .setMediaId("sample")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("A song with a rather long title that does not fit")
                    .setSubtitle("Artist · Album")
                    .build()
            )
            .build(),
        isPlaying = isPlaying,
        progress = { 0.4f },
        onTogglePlay = {},
        onNext = {},
        onClick = {},
    )
}

@Preview(showBackground = true, name = "Playing, light")
@Composable
private fun MiniPlayerPlayingPreview() {
    KreateTheme(ThemeMode.LIGHT, ContrastLevel.NORMAL, Color.Unspecified) {
        MiniPlayerSample(isPlaying = true)
    }
}

@Preview(showBackground = true, name = "Paused, dark")
@Composable
private fun MiniPlayerPausedPreview() {
    KreateTheme(ThemeMode.DARK, ContrastLevel.NORMAL, Color.Unspecified) {
        MiniPlayerSample(isPlaying = false)
    }
}
