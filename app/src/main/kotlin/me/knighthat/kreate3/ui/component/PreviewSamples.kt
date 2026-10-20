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

import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.theme.KreateTheme

/*
 * Made up items and the theme for previews, never used by the app itself.
 */

/**
 * A whole screen on a phone, once light and once dark. Wrap the screen in [PreviewTheme].
 */
@Preview(name = "Light", showBackground = true, device = "spec:width=411dp,height=891dp")
@Preview(
    name = "Dark",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ScreenPreviews

/**
 * A component as large as it is, once light and once dark. Wrap it in [PreviewTheme].
 */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ComponentPreviews

/** The app's theme, light or dark like the preview's system. */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) =
    KreateTheme(ThemeMode.AUTOMATIC, ContrastLevel.NORMAL, Color.Unspecified, content)

/** An item that shows [title] and [subtitle], from [origin] when it is given. */
@OptIn(UnstableApi::class)
fun previewItem(
    title: String,
    subtitle: String,
    durationMs: Long? = null,
    origin: Origin? = null,
    explicit: Boolean = false,
): MediaItem =
    MediaItem.Builder()
        .setMediaId("${origin?.name}:$title")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDurationMs(durationMs)
                .setExtras(Bundle().apply {
                    origin?.let { putString(EXTRA_ORIGIN, it.name) }
                    putBoolean(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, explicit)
                })
                .build()
        )
        .build()

/** Songs of every origin, some explicit. */
fun previewSongs(origin: Origin = Origin.LOCAL): List<MediaItem> = listOf(
    previewItem("Midnight Drive", "Neon Coast · Afterglow", 214_000, origin),
    previewItem("Paper Planes", "The Folding Kind · Origami", 187_000, origin, explicit = true),
    previewItem(
        "A song with a rather long title that does not fit",
        "Artist · Album",
        305_000,
        origin
    ),
    previewItem("Low Tide", "Harbour Lights · Low Tide", 242_000, origin),
    previewItem("Static", "Radio Ghosts · Frequencies", 198_000, origin),
    previewItem("Golden Hour", "Neon Coast · Afterglow", 226_000, origin),
)

fun previewAlbums(origin: Origin = Origin.LOCAL): List<MediaItem> = listOf(
    previewItem("Afterglow", "Neon Coast · 2021", origin = origin),
    previewItem("Origami", "The Folding Kind · 2019", origin = origin, explicit = true),
    previewItem("Low Tide", "Harbour Lights · 2023", origin = origin),
    previewItem("Frequencies", "Radio Ghosts · 2018", origin = origin),
    previewItem("Northern Lines", "Cartographers · 2020", origin = origin),
    previewItem("Weekend", "Sunday Club · 2024", origin = origin),
)

fun previewArtists(origin: Origin = Origin.LOCAL): List<MediaItem> = listOf(
    previewItem("Neon Coast", "12 songs", origin = origin),
    previewItem("The Folding Kind", "8 songs", origin = origin),
    previewItem("Harbour Lights", "5 songs", origin = origin),
    previewItem("Radio Ghosts", "1 song", origin = origin),
    previewItem("Cartographers", "9 songs", origin = origin),
    previewItem("Sunday Club", "3 songs", origin = origin),
)

fun previewPlaylists(origin: Origin = Origin.LOCAL): List<MediaItem> = listOf(
    previewItem("Road trip", "On this device", origin = origin),
    previewItem("October 2026", "Monthly · On this device", origin = origin),
    previewItem("Focus", "On this device", origin = origin),
    previewItem("Gym", "On this device", origin = origin),
)
