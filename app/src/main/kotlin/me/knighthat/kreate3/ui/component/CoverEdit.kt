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

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R

/** How much a cover darkens under the handle or the remove button over it. */
private const val COVER_SCRIM_ALPHA = 0.55f

/** What the covers of an editable list of songs do, like the queue or a playlist. */
enum class CoverEditMode {
    /** Nothing, they are only covers. */
    NONE,

    /** Each one is a handle to drag its song to another place in the list. */
    REORDER,

    /** Each one is a button that takes its song out of the list. */
    REMOVE,
}

/**
 * A handle to drag a song, alone where the cover would be, for [SongItem]'s `thumbnailOverlay` in
 * rows that leave the cover out, like an album's.
 *
 * @param handle the item's `draggableHandle` of sh.calvin.reorderable
 */
@Composable
internal fun BoxScope.DragHandle(handle: Modifier) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .matchParentSize()
        .then(handle),
) {
    Icon(
        painterResource(R.drawable.drag_handle),
        contentDescription = stringResource(R.string.semantic_drag_to_move),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What [mode] puts over a song's cover, for [SongItem]'s `thumbnailOverlay`: nothing, a drag
 * handle that [handle] makes one, or a button that calls [onRemove].
 *
 * @param handle the item's `draggableHandle` of sh.calvin.reorderable
 * @param removeDescription what the remove button does, for accessibility
 */
@Composable
internal fun BoxScope.CoverEditOverlay(
    mode: CoverEditMode,
    handle: Modifier,
    removeDescription: String,
    onRemove: () -> Unit,
) = Crossfade(mode, Modifier.matchParentSize(), label = "cover") { shown ->
    when (shown) {
        CoverEditMode.NONE -> {}
        CoverEditMode.REORDER -> CoverIcon(
            R.drawable.drag_handle,
            stringResource(R.string.semantic_drag_to_move),
            handle
        )
        CoverEditMode.REMOVE -> Tooltip(removeDescription) {
            CoverIcon(
                icon = R.drawable.close,
                description = removeDescription,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
    }
}

/** [icon] in white over the cover darkened, in the cover's shape. */
@Composable
private fun CoverIcon(
    @DrawableRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .fillMaxSize()
        .clip(MaterialTheme.shapes.small)
        .background(Color.Black.copy(alpha = COVER_SCRIM_ALPHA))
        .then(modifier),
) {
    Icon(painterResource(icon), contentDescription = description, tint = Color.White)
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun CoverEditPreview() = PreviewTheme {
    val (none, reorder, remove, handle) = previewSongs()

    Surface {
        Column(modifier = Modifier.width(360.dp)) {
            CoverEditMode.entries.zip(listOf(none, reorder, remove)).forEach { (mode, song) ->
                SongItem(song, onClick = {}, thumbnailOverlay = {
                    CoverEditOverlay(mode, Modifier, "Remove from playlist", onRemove = {})
                })
            }
            SongItem(
                handle,
                onClick = {},
                showThumbnail = false,
                thumbnailOverlay = { DragHandle(Modifier) }
            )
        }
    }
}
//</editor-fold>
