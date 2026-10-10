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

package me.knighthat.kreate.database.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One listening session of a [Song] by the active user.
 *
 * @property id id of the entry
 * @property songId the [Song] that was played
 * @property timeSpent how long the song was played, accurate to the second
 * @property createdAt when the entry was recorded
 */
data class PlaybackHistory(
    val id: Long,
    val songId: String,
    val timeSpent: Duration,
    val createdAt: Instant,
)
