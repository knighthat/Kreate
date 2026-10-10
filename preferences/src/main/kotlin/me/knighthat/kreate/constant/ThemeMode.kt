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
package me.knighthat.kreate.constant

enum class ThemeMode {
    /** Follows the system's light/dark mode. */
    AUTOMATIC,
    AUTOMATIC_DYNAMIC,
    LIGHT,
    LIGHT_DYNAMIC,
    DARK,
    DARK_DYNAMIC,

    /** Builds the palette from a seed color, using it as the primary color. */
    THUMBNAIL,
}
