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

import kotlin.time.Duration
import kotlin.time.Instant

/** A span of the history shown as one group: plays from [start] up to, not including, [end]. */
data class PlayPeriod(val start: Instant, val end: Instant)

/**
 * A play of [song] by the active user, in a [PlayPeriod].
 *
 * @property period the index of the period in the list asked for
 * @property id the play's id, see [PlaybackHistory.id]
 */
data class PeriodPlay(
    val period: Int,
    val id: Long,
    val song: UserSongView,
    val playedAt: Instant,
)

/**
 * How many plays and songs the [period]-th [PlayPeriod] asked for has, and for how long all plays
 * together.
 */
data class PeriodSummary(
    val period: Int,
    val plays: Int,
    val songs: Int,
    val timeSpent: Duration,
)
