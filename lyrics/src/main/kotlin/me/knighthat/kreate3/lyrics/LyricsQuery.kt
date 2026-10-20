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

package me.knighthat.kreate3.lyrics

import me.knighthat.kreate3.model.Origin
import kotlin.time.Duration

/**
 * The song to find lyrics for. A source of the song's [origin] can look it up by [songId]; the
 * others search by its title, artists, album and duration.
 *
 * @property songId id of the song at its [origin]
 * @property artists names of the artists, the main one first
 * @property album name of the album, `null` when it has none or it isn't known
 * @property duration length of the song, `null` when unknown; searches use it to tell versions
 * of a song apart
 */
data class LyricsQuery(
    val songId: String,
    val origin: Origin,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val duration: Duration? = null,
)
