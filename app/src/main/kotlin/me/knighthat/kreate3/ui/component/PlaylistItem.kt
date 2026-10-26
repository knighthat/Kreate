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

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.materialkolor.hct.Hct
import me.knighthat.kreate3.R

/** How many covers make a grid, fewer show the first one alone. */
private const val GRID_COVERS = 4

/** Colorfulness of the background behind a playlist's initial, in HCT chroma. */
private const val INITIAL_CHROMA = 36.0

/**
 * Whether a playlist with no picture and no song covers shows the first letter of its name
 * instead of an icon, see `Preferences.PLAYLIST_INITIAL`. Off unless provided, like in previews.
 */
val LocalPlaylistInitial = compositionLocalOf { false }

/**
 * What Coil loads for each cover of the playlist [this], see [EXTRA_COVERS], most played first.
 */
private val MediaItem.playlistCovers: List<Any>
    get() {
        val extras = mediaMetadata.extras ?: return emptyList()
        val covers = extras.getStringArray(EXTRA_COVERS) ?: return emptyList()
        val localIds = extras.getStringArray(EXTRA_COVER_LOCAL_IDS)
        return covers.mapIndexedNotNull { index, cover ->
            thumbnailModel(cover.toUri(), localIds?.getOrNull(index)?.toLongOrNull())
        }
    }

/**
 * A playlist as a row: cover, name, and where it comes from under it.
 * An icon above the name tells a playlist that lives on this device.
 *
 * Without a picture of its own, the playlist shows the covers of its most played songs: four
 * of them in a 2 × 2 grid, or the most played one alone when there are fewer. With no covers
 * either, it shows a playlist icon, or the first letter of its name ([LocalPlaylistInitial]).
 */
@Composable
fun PlaylistItem(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val covers = remember(item) { item.playlistCovers }

    when {
        item.mediaMetadata.artworkUri != null ->
            ItemRow(item = item, onClick = onClick, modifier = modifier)

        covers.size in 1 until GRID_COVERS ->
            ItemRow(
                item = item,
                onClick = onClick,
                modifier = modifier,
                thumbnails = covers.take(1)
            )

        // The grid or the empty cover takes the place of the cover, ItemRow draws the overlay
        // alone in its box
        else -> ItemRow(
            item = item,
            onClick = onClick,
            modifier = modifier,
            showThumbnail = false,
            thumbnailOverlay = {
                if (covers.isEmpty())
                    EmptyCover(
                        item.mediaMetadata.title?.toString().orEmpty(),
                        Modifier.matchParentSize()
                    )
                else CoverGrid(covers, Modifier.matchParentSize())
            },
        )
    }
}

/**
 * The first four of [covers] in two rows of two, each with the fallback of [Thumbnail].
 */
@Composable
private fun CoverGrid(covers: List<Any>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(MaterialTheme.shapes.small)) {
        covers.take(GRID_COVERS).chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                row.forEach {
                    Thumbnail(
                        models = listOf(it),
                        shape = RectangleShape,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * The cover of a playlist called [name] that has nothing to show: the first letter or digit of
 * [name] when [LocalPlaylistInitial] is on and it has one, a playlist icon otherwise.
 */
@Composable
private fun EmptyCover(name: String, modifier: Modifier = Modifier) {
    val initial = name.firstOrNull { it.isLetterOrDigit() }
        ?.takeIf { LocalPlaylistInitial.current }
        ?.uppercase()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Thumbnail(
        models = emptyList(),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        placeholder = {
            if (initial == null)
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            else {
                val (background, content) = remember(name, dark) { initialColors(name, dark) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .matchParentSize()
                        .background(background),
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = content
                    )
                }
            }
        },
    )
}

/**
 * Background and text color for the initial of a playlist called [name]: a hue picked from the
 * name, so a playlist keeps its color, at tones that read well on a [dark] or light theme.
 */
private fun initialColors(name: String, dark: Boolean): Pair<Color, Color> {
    val hue = name.hashCode().mod(360).toDouble()
    val background = Hct.from(hue, INITIAL_CHROMA, if (dark) 30.0 else 90.0)
    val content = Hct.from(hue, INITIAL_CHROMA, if (dark) 90.0 else 10.0)
    return Color(background.toInt()) to Color(content.toInt())
}

//<editor-fold defaultstate="collapsed" desc="Previews">
/** [item] with [count] song covers, that don't load in a preview. */
private fun withCovers(item: MediaItem, count: Int): MediaItem = item.buildUpon()
    .setMediaMetadata(
        item.mediaMetadata.buildUpon()
            .setExtras(Bundle(item.mediaMetadata.extras).apply {
                putStringArray(EXTRA_COVERS, Array(count) { "preview://cover/$it" })
            })
            .build()
    )
    .build()

@ComponentPreviews
@Composable
private fun PlaylistItemPreview() = PreviewTheme {
    val (roadTrip, monthly, focus, gym) = previewPlaylists()

    Surface {
        Column(modifier = Modifier.width(360.dp)) {
            PlaylistItem(roadTrip, onClick = {})
            CompositionLocalProvider(LocalPlaylistInitial provides true) {
                PlaylistItem(monthly, onClick = {})
            }
            PlaylistItem(withCovers(focus, 1), onClick = {})
            PlaylistItem(withCovers(gym, GRID_COVERS), onClick = {})
        }
    }
}
//</editor-fold>
