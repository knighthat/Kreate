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

package me.knighthat.kreate3.database.model

import android.net.Uri
import me.knighthat.kreate3.model.Origin
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A song in the active user's library as the user sees it: every value is the user's own
 * when set, the shared one otherwise.
 *
 * @property songId id of the song at its [origin]
 * @property origin where the song comes from
 * @property name title, `null` when unknown
 * @property artists artist names, the user's own list when set. The shared list has no defined order
 * @property artistIds ids of the credited artists, in no defined order
 * @property thumbnail cover: the user's, then the song's, then its album's
 * @property duration length of the song, accurate to the second
 * @property isExplicit `null` when unknown
 * @property albumId album the song belongs to, `null` for none
 * @property albumName title of the album as the user sees it
 * @property albumPosition position of the song in its album
 * @property unavailableSince when a scan first missed the song, `null` while it is available
 * @property likedAt when the user liked the song, `null` if not liked
 */
data class UserSongView(
    val songId: String,
    val origin: Origin,
    val name: String? = null,
    val artists: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    val thumbnail: Uri? = null,
    val duration: Duration? = null,
    val isExplicit: Boolean? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val albumPosition: Int? = null,
    val unavailableSince: Instant? = null,
    val likedAt: Instant? = null,
) {

    /**
     * Whether the file was found by the last scan. Unavailable songs should be hidden or skipped.
     */
    val isAvailable: Boolean get() = unavailableSince == null
}
