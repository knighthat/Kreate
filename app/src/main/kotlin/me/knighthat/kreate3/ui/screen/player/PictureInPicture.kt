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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.lyrics.SyncedLyrics
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.ComponentPreviews
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.rememberSongThumbnails

/** 16:9, wide, for the lyrics. */
internal const val LYRICS_WINDOW_WIDTH = 16
internal const val LYRICS_WINDOW_HEIGHT = 9

/** The range of [Preferences.PICTURE_IN_PICTURE_LYRICS_TEXT_SIZE] in percent. */
internal val PICTURE_IN_PICTURE_TEXT_SIZES = 20..100

/**
 * What the picture-in-picture window shows and whether it is open (see
 * [Preferences.PICTURE_IN_PICTURE][me.knighthat.kreate3.preferences.Preferences.PICTURE_IN_PICTURE]).
 * The activity sizes and fills the window from it, the player says what the user was looking at.
 */
@Stable
class PictureInPicture {

    /** Whether the window is open, the app is drawn as [PictureInPictureView] while it is. */
    var isActive by mutableStateOf(false)

    /** Whether the full player is open on its lyrics page, which makes the window wide. */
    var onLyricsPage by mutableStateOf(false)
        internal set

    /**
     * The lyrics of the song playing when they are on the lyrics page and timed, so the window
     * follows them. `null` for the cover.
     */
    internal var lyrics by mutableStateOf<SyncedLyrics?>(null)
}

/**
 * Draws the picture-in-picture window of [pip] over the app while it is open: the lyrics
 * following [item] when [PictureInPicture.lyrics] has them, else its cover. Its buttons are the
 * window's own, see `PictureInPictureWindow`.
 */
@Composable
fun PictureInPictureView(
    pip: PictureInPicture,
    item: MediaItem?,
    isPlaying: Boolean,
    controller: PlaybackController,
    modifier: Modifier = Modifier,
) {
    if (!pip.isActive) return

    val covers = rememberSongThumbnails(item)
    val lyrics = pip.lyrics
    if (lyrics != null) {
        // Everything in the list is sized for the whole screen, the small window shrinks it
        val textSize by Preferences.PICTURE_IN_PICTURE_LYRICS_TEXT_SIZE.collectAsStateWithLifecycle()
        val density = LocalDensity.current
        val scaled = remember(density, textSize) {
            Density(density.density * textSize / 100f, density.fontScale)
        }
        CompositionLocalProvider(LocalDensity provides scaled) {
            Box(modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)) {
                SyncedLyricsList(
                    lyrics = lyrics,
                    footer = "",
                    isPlaying = isPlaying,
                    controller = controller,
                    contentPadding = PaddingValues(vertical = 8.dp),
                )
            }
        }
    } else {
        PictureInPictureCover(covers, filled = !pip.onLyricsPage, modifier = modifier)
    }
}

/**
 * The cover filling the window, or in the middle of it when the window is wide for lyrics that
 * aren't there to show ([filled] is `false`).
 */
@Composable
private fun PictureInPictureCover(
    covers: List<Any>,
    filled: Boolean,
    modifier: Modifier = Modifier,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface),
) {
    if (filled)
        Thumbnail(models = covers, shape = RectangleShape, modifier = Modifier.fillMaxSize())
    else
        Thumbnail(
            models = covers,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .padding(8.dp),
        )
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun PictureInPictureCoverPreview() = PreviewTheme {
    PictureInPictureCover(covers = emptyList(), filled = true)
}

@ComponentPreviews
@Composable
private fun PictureInPictureTallCoverPreview() = PreviewTheme {
    PictureInPictureCover(covers = emptyList(), filled = false)
}
//</editor-fold>
