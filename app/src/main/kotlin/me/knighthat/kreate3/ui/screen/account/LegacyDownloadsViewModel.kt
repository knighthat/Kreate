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

package me.knighthat.kreate3.ui.screen.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.download.LegacyDownloads
import me.knighthat.kreate3.provider.youtube.YouTubeException

/**
 * How far the songs of the old app are looked up.
 *
 * @property found songs that were found
 * @property done ids asked about so far, found or not
 * @property total ids to ask about
 * @property failed whether YouTube could not be reached, the rest was not looked up
 */
data class LegacyLoading(
    val found: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val failed: Boolean = false,
) {

    val isLoading: Boolean get() = !failed && done < total
}

/**
 * The songs the old app had downloaded, looked up a hundred at a time as the page opens and shown
 * as they come, for the user to pick the ones to download. Nothing is picked at first.
 */
class LegacyDownloadsViewModel(private val legacy: LegacyDownloads) : ViewModel() {

    private val ids = legacy.pending.value

    private val _songs = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _selected = MutableStateFlow(emptySet<String>())
    private val _loading = MutableStateFlow(LegacyLoading(total = ids.size))

    /** The songs found so far, in the order of the old app's list. */
    val songs: StateFlow<List<MediaItem>> = _songs.asStateFlow()

    /** Ids of the songs picked. */
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    val loading: StateFlow<LegacyLoading> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                for (chunk in ids.chunked(LegacyDownloads.BATCH)) {
                    val found = legacy.details(chunk)
                    _songs.update { it + found }
                    _loading.update {
                        it.copy(
                            found = it.found + found.size,
                            done = it.done + chunk.size
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                Logger.w(e, "LegacyDownloads") { "Couldn't look up the songs of the old app" }
                _loading.update { it.copy(failed = true) }
            }
        }
    }

    fun toggle(id: String) {
        _selected.update { if (id in it) it - id else it + id }
    }

    fun clear() {
        _selected.value = emptySet()
    }

    /** Picks every song found so far. */
    fun selectAll() {
        _selected.value = _songs.value.mapTo(HashSet()) { it.mediaId }
    }

    /**
     * Downloads the songs picked and forgets the rest of the list, called when the page is left.
     * Nothing picked is allowed: nothing is downloaded then.
     */
    fun download() {
        val picked = _selected.value
        legacy.download(_songs.value.filter { it.mediaId in picked })
    }
}
