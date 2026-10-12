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

package me.knighthat.kreate.database.repository

/**
 * The `LIKE` pattern that matches text containing [query], used with `ESCAPE '\'`.
 * `%` and `_` in the query are taken literally.
 */
internal fun containsPattern(query: String): String =
    "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/**
 * The `LIKE` pattern that matches text starting with [query], used with `ESCAPE '\'`.
 * `%` and `_` in the query are taken literally.
 */
internal fun prefixPattern(query: String): String =
    containsPattern(query).removePrefix("%")
