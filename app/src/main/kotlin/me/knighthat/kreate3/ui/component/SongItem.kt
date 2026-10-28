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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.knighthat.kreate3.model.Origin

/** Where a row is laid out, so its menu can lift it from there. */
private class RowAnchor {
    var coordinates: LayoutCoordinates? = null
}

/**
 * A song as a row: cover, title, its artists under it, and the length at the end, level with
 * the bottom line. Icons above the title tell a file on this device and explicit songs, and the
 * album is on that line at the end. Pass `showThumbnail = false` and `showAlbum = false` inside
 * its album, where the cover and the name are already on screen.
 *
 * A long press lifts the row above the [SongMenu], unless [hasMenu] is `false`. The row stays
 * empty in the list meanwhile.
 *
 * @param menuActions what the menu offers for the list the song is in, after the song's own
 * @param trailing what the row shows at the end in place of the length
 * @param overline shown above the title in place of the album, in [overlineColor]
 * @param showBadges whether the icons above the title show
 * @param showDisliked whether a disliked song is marked among them, `false` where every song is
 */
@Composable
fun SongItem(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
    showAlbum: Boolean = true,
    thumbnailOverlay: (@Composable BoxScope.() -> Unit)? = null,
    hasMenu: Boolean = true,
    menuActions: List<SongMenuAction> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
    overline: String? = null,
    overlineColor: Color = Color.Unspecified,
    showBadges: Boolean = true,
    showDisliked: Boolean = true,
) {
    val host = LocalSongMenuHost.current
    val anchor = remember { RowAnchor() }
    val lifted by remember(host, anchor) { derivedStateOf { host?.request?.owner === anchor } }

    SongRow(
        item = item,
        onClick = onClick,
        modifier = modifier
            .onGloballyPositioned { anchor.coordinates = it }
            .graphicsLayer { alpha = if (lifted) 0f else 1f },
        showThumbnail = showThumbnail,
        showAlbum = showAlbum,
        thumbnailOverlay = thumbnailOverlay,
        trailing = trailing,
        overline = overline,
        overlineColor = overlineColor,
        showBadges = showBadges,
        showDisliked = showDisliked,
        onLongClick = if (hasMenu && host != null) {
            {
                anchor.coordinates?.takeIf { it.isAttached }?.let {
                    host.request = SongMenuRequest(
                        owner = anchor,
                        item = item,
                        rowBounds = Rect(it.positionOnScreen(), it.size.toSize()),
                        showThumbnail = showThumbnail,
                        showAlbum = showAlbum,
                        actions = menuActions,
                        trailing = trailing,
                        overline = overline,
                        overlineColor = overlineColor,
                        showBadges = showBadges,
                        showDisliked = showDisliked,
                    )
                }
            }
        } else null,
    )
}

/** What [SongItem] shows, also lifted above its menu. */
@Composable
internal fun SongRow(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
    showAlbum: Boolean = true,
    thumbnailOverlay: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    overline: String? = null,
    overlineColor: Color = Color.Unspecified,
    showBadges: Boolean = true,
    showDisliked: Boolean = true,
) {
    val duration = formatDuration(item.mediaMetadata.durationMs)

    ItemRow(
        item = item,
        onClick = onClick,
        modifier = modifier,
        showThumbnail = showThumbnail,
        overlineEnd = overline ?: item.mediaMetadata.albumTitle?.toString()?.takeIf { showAlbum },
        thumbnailOverlay = thumbnailOverlay,
        trailing = trailing ?: duration?.let {
            { Text(it, style = MaterialTheme.typography.labelSmall) }
        },
        onLongClick = onLongClick,
        showBadges = showBadges,
        overlineColor = overlineColor,
        showDisliked = showDisliked,
    )
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun SongItemPreview() = PreviewTheme {
    val liked = previewItem(
        "Liked song",
        "Neon Coast",
        226_000,
        Origin.OPEN_SUBSONIC,
        album = "Afterglow",
        mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        rated = true,
    )

    Surface {
        Column(modifier = Modifier.width(360.dp)) {
            previewSongs().take(3).forEach { SongItem(it, onClick = {}) }
            SongItem(liked, onClick = {})
            // Inside its album
            SongItem(liked, onClick = {}, showThumbnail = false, showAlbum = false)
            // When it was played, in place of its length
            SongItem(liked, onClick = {}, trailing = { Text("9:41 PM") })
        }
    }
}
//</editor-fold>
