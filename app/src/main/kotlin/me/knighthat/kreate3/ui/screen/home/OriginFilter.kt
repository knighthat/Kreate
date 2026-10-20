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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.ui.component.origin
import kotlin.random.Random

/**
 * The origins a home tab shows, kept in [preference]. While none is selected, every origin shows.
 *
 * @param stored the origins of the tab's items in the database
 * @param liveItems the tab's items fetched live, unfiltered
 */
class OriginFilter(
    scope: CoroutineScope,
    private val preference: Preference<Set<String>>,
    stored: Flow<Set<Origin>>,
    liveItems: StateFlow<List<MediaItem>> = MutableStateFlow(emptyList()),
) {

    val selected: StateFlow<Set<Origin>> = preference.state
        .map { it.toOrigins() }
        .stateIn(scope, SharingStarted.Eagerly, preference.value.toOrigins())

    /**
     * The origins to offer a chip for: the ones the tab has items from, and the selected ones so
     * they can be turned off. Empty when there's nothing to choose between.
     */
    val choices: StateFlow<List<Origin>> =
        combine(stored, liveItems, selected) { db, live, chosen ->
            val offered = db + live.mapNotNull { it.origin } + chosen
            if (offered.size <= 1 && chosen.isEmpty()) emptyList()
            else Origin.entries.filter { it in offered }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The items of `liveItems` the filter shows. */
    val live: StateFlow<List<MediaItem>> = combine(liveItems, selected) { list, origins ->
        list.filterBy(origins)
    }.stateIn(scope, SharingStarted.Eagerly, liveItems.value.filterBy(selected.value))

    /** Shows the items of [origin] when they're hidden, hides them otherwise. */
    fun toggle(origin: Origin) = preference.update {
        if (origin.name in it) it - origin.name else it + origin.name
    }

    private fun List<MediaItem>.filterBy(origins: Set<Origin>) =
        if (origins.isEmpty()) this else filter { it.origin in origins }

    // Names of origins that no longer exist are left out
    private fun Set<String>.toOrigins(): Set<Origin> =
        Origin.entries.filterTo(mutableSetOf()) { it.name in this }
}

/**
 * One item at random, each of the [stored] items of the database and of [live] as likely as the
 * others. `null` when there is none.
 *
 * @param pickStored picks one of the database's items at random
 */
suspend fun pickRandom(
    stored: Int,
    live: List<MediaItem>,
    pickStored: suspend () -> MediaItem?,
): MediaItem? {
    val total = stored + live.size
    if (total == 0) return null

    val index = Random.nextInt(total)
    return if (index < stored) pickStored() else live[index - stored]
}
