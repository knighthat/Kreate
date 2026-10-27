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

import me.knighthat.kreate3.model.Origin
import kotlin.time.Duration

/**
 * How much the active user listened in a span of time.
 *
 * @property songs different songs played
 * @property artists different artists credited on those songs
 */
data class ListeningSummary(
    val plays: Int,
    val songs: Int,
    val artists: Int,
    val timeSpent: Duration,
)

/**
 * A song ranked by [listens]: the time spent on it over its length, so a 3 minute song played
 * for 9 minutes was listened to 3 times, whatever the number of plays. A song of unknown length
 * counts a listen per play.
 */
data class TopSong(val song: UserSongView, val listens: Double, val timeSpent: Duration)

/** An album ranked by the [TopSong.listens] of its songs added up. */
data class TopAlbum(val album: UserAlbumView, val listens: Double, val timeSpent: Duration)

/**
 * An artist ranked by the [TopSong.listens] of the songs crediting them added up. A song with
 * several artists counts in full for each.
 */
data class TopArtist(val artist: UserArtistView, val listens: Double, val timeSpent: Duration)

/** How long the songs of [origin] were listened to. */
data class OriginTime(val origin: Origin, val timeSpent: Duration)
