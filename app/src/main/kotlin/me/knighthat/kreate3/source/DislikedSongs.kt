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

package me.knighthat.kreate3.source

import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.knighthat.kreate3.ui.navigation.Tab

// Disliked songs are never shown, the database leaves them out of its lists. These hide them
// from the lists fetched live, with the ids of UserSongRepository.dislikedIds.

/** The songs whose id isn't in [disliked], in the same order. */
internal fun List<MediaItem>.withoutDisliked(disliked: Set<String>): List<MediaItem> =
    if (disliked.isEmpty()) this else filterNot { it.mediaId in disliked }

/** The items without the songs whose id is in [disliked], albums, artists and playlists stay. */
internal fun List<Pair<Tab, MediaItem>>.withoutDislikedSongs(
    disliked: Set<String>,
): List<Pair<Tab, MediaItem>> =
    if (disliked.isEmpty()) this
    else filterNot { (tab, item) -> tab == Tab.SONGS && item.mediaId in disliked }

/**
 * The pages without the songs whose id is in [disliked], also the ones disliked while they show.
 * Call it after `cachedIn`, the pages are filtered again at every change of [disliked].
 *
 * @param songIdOf the id of the song an item is, `null` for an item that isn't a song
 */
internal fun <T : Any> Flow<PagingData<T>>.hidingDisliked(
    disliked: Flow<Set<String>>,
    songIdOf: (T) -> String?,
): Flow<PagingData<T>> =
    combine(this, disliked) { page, ids ->
        if (ids.isEmpty()) page else page.filter { songIdOf(it) !in ids }
    }
