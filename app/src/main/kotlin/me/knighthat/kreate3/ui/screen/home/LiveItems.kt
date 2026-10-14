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

package me.knighthat.kreate3.ui.screen.home

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.knighthat.kreate3.ui.component.origin

/**
 * Items of the external sources a home tab shows after the ones in the database. They are only
 * kept while the screen lives, never stored.
 *
 * @param fetch gives every source's items, see `liveAlbums`
 */
class LiveItems(
    private val scope: CoroutineScope,
    private val fetch: suspend () -> List<MediaItem>,
) {

    private val _items = MutableStateFlow(emptyList<MediaItem>())
    private var job: Job? = null

    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    /** Fetches the items again, unless they are on their way already. */
    fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch { _items.value = fetch() }
    }
}

/** Key of a live item in a list that also holds items of the database. */
val MediaItem.liveKey: String
    get() = "live:${origin?.name}:$mediaId"
