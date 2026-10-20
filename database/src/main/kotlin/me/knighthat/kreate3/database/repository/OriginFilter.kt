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

package me.knighthat.kreate3.database.repository

import me.knighthat.kreate3.model.Origin

/**
 * The condition that keeps rows from [origins], to put after a `WHERE`. Nothing when [origins]
 * is empty, every origin is kept then. The names are written into the query, they are the
 * enum's, never the user's input.
 */
internal fun originFilter(origins: Set<Origin>): String =
    if (origins.isEmpty()) ""
    else origins.joinToString(prefix = "AND origin IN (", postfix = ")") { "'${it.name}'" }
