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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.component.SongItem

/** How strongly the song playing stands out from the rest of the queue. */
private const val CURRENT_HIGHLIGHT_ALPHA = 0.16f

/**
 * Every song of the queue, the one playing highlighted. It opens with that song near the top,
 * the one before it above. Tapping a song plays it.
 *
 * @param currentIndex where the song playing is in the queue, `-1` when none is
 */
@Composable
internal fun QueuePage(
    controller: PlaybackController,
    currentIndex: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val queue by controller.queue.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)
    )
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = CURRENT_HIGHLIGHT_ALPHA)

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        // No keys, a song can be queued more than once
        itemsIndexed(queue) { index, item ->
            SongItem(
                item = item,
                onClick = { controller.skipTo(index) },
                modifier = if (index == currentIndex) Modifier.background(highlight) else Modifier,
            )
        }
    }
}
