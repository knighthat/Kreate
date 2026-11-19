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

package me.knighthat.kreate3.presence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.knighthat.kreate3.model.Origin
import org.koin.compose.koinInject

/** What the user is doing in the app, for their Discord profile. */
sealed interface Doing {

    /**
     * A song is in the player, playing or paused.
     *
     * @property position where the song was, in milliseconds, at [at]
     * @property duration of the song in milliseconds, `null` when not known
     * @property at when [position] was read, in milliseconds since the epoch
     */
    data class Listening(
        val item: MediaItem,
        val playing: Boolean,
        val position: Long,
        val duration: Long?,
        val at: Long,
    ) : Doing

    /**
     * A page of an album, an artist or a playlist is open.
     *
     * @property creator who made it: an album's artists, a playlist's owner; `null` for an
     * artist or when unknown
     * @property models what the image loader takes for its picture, in the order to try
     * @property link where anyone can open it, `null` when there is none
     * @property isPrivate whether only its owner can see it, a private playlist
     * @property since when it was opened, in milliseconds since the epoch
     */
    data class Browsing(
        val kind: BrowsedKind,
        val origin: Origin?,
        val name: String,
        val creator: String?,
        val models: List<Any>,
        val link: String?,
        val isPrivate: Boolean = false,
        val since: Long = System.currentTimeMillis(),
    ) : Doing

    /** The results of a search are open. */
    data class Searching(
        val query: String,
        val origin: Origin?,
        val since: Long = System.currentTimeMillis(),
    ) : Doing
}

enum class BrowsedKind { ALBUM, ARTIST, PLAYLIST }

/**
 * What the app is doing now: the player says what it plays, screens what they show. The song in
 * the player wins over any screen, see `DiscordPresence`.
 */
class PresenceState {

    private val _listening = MutableStateFlow<Doing.Listening?>(null)
    private val _browsing = MutableStateFlow<Doing?>(null)

    /** The song in the player, `null` while it's stopped. */
    val listening: StateFlow<Doing.Listening?> = _listening.asStateFlow()

    /** The page on screen, `null` while none says what it shows or the app isn't on screen. */
    val browsing: StateFlow<Doing?> = _browsing.asStateFlow()

    fun play(listening: Doing.Listening?) {
        _listening.value = listening
    }

    fun browse(doing: Doing) {
        _browsing.value = doing
    }

    /** Takes [doing] away, unless another page said what it shows since. */
    fun leave(doing: Doing) {
        _browsing.compareAndSet(doing, null)
    }
}

/**
 * Says the page shows [doing] while it's on screen, `null` for nothing. A new value replaces the
 * one before, like once the page loaded its picture.
 */
@Composable
fun ShowOnDiscord(doing: Doing?) {
    // Previews have no Koin, and nothing to tell
    if (LocalInspectionMode.current) return
    val state = koinInject<PresenceState>()
    LifecycleStartEffect(doing) {
        doing?.let(state::browse)
        onStopOrDispose { doing?.let(state::leave) }
    }
}

/**
 * Says an album, artist or playlist page shows [name] while it's on screen, see [ShowOnDiscord];
 * nothing while the name is blank, like while it loads.
 *
 * @param creator who made it, see [Doing.Browsing.creator]
 * @param models what the image loader takes for its picture
 * @param link where anyone can open it, `null` for none
 * @param isPrivate whether only its owner can see it, see [Doing.Browsing.isPrivate]
 */
@Composable
fun ShowBrowsingOnDiscord(
    kind: BrowsedKind,
    origin: Origin?,
    name: String,
    creator: String?,
    models: List<Any>,
    link: String? = null,
    isPrivate: Boolean = false,
) {
    // Counted from when the page opened, not from each time it loads more
    val since = remember { System.currentTimeMillis() }
    val doing = remember(kind, origin, name, creator, models, link, isPrivate) {
        name.takeIf { it.isNotBlank() }
            ?.let { Doing.Browsing(kind, origin, it, creator, models, link, isPrivate, since) }
    }
    ShowOnDiscord(doing)
}

/** Says the results of [query] are on screen, see [ShowOnDiscord]. */
@Composable
fun ShowSearchingOnDiscord(query: String, origin: Origin?) {
    val since = remember { System.currentTimeMillis() }
    val doing = remember(query, origin) {
        query.takeIf { it.isNotBlank() }?.let { Doing.Searching(it, origin, since) }
    }
    ShowOnDiscord(doing)
}
