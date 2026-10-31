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
import kotlin.time.Instant

/**
 * What the active user keeps about a [Song]. [name], [artists], [thumbnail] and [albumPosition]
 * override the shared values.
 *
 * @property songId the [Song] this is about
 * @property name title preferred by the user, `null` to use the shared one
 * @property artists artist names preferred by the user, `null` to use the shared ones
 * @property thumbnail image preferred by the user, `null` to use the shared one
 * @property likedAt when the user liked the song, `null` if neither liked nor disliked; a dislike
 * is the time it was made, made negative
 * @property albumPosition where the user put the song in its album, lowest first, `null` to use
 * the shared position
 * @property downloadedAt when the user downloaded the song, `null` if they didn't
 * @property musicBrainzId the MusicBrainz recording the song was last identified as, `null` while
 * it never was
 */
data class UserSong(
    val songId: String,
    val name: String? = null,
    val artists: List<String>? = null,
    val thumbnail: Uri? = null,
    val likedAt: Instant? = null,
    val albumPosition: Int? = null,
    val downloadedAt: Instant? = null,
    val musicBrainzId: String? = null,
)
