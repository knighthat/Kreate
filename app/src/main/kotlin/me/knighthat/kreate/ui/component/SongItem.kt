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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem

/**
 * A song as a row: cover, title, "artist · album" under it, and the length at the end, level
 * with the bottom line. Icons above the title tell a file on this device and explicit songs.
 * Pass `showThumbnail = false` where the cover is already on screen, like inside its album.
 */
@Composable
fun SongItem(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
) {
    val duration = formatDuration(item.mediaMetadata.durationMs)

    ItemRow(
        item = item,
        onClick = onClick,
        modifier = modifier,
        showThumbnail = showThumbnail,
        trailing = duration?.let {
            { Text(it, style = MaterialTheme.typography.labelSmall) }
        },
    )
}
