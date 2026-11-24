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

package me.knighthat.kreate3.constant

/**
 * What the app does about new releases when it comes on screen, at most once a day. Only builds
 * that update themselves (github) read it.
 */
enum class AutoUpdateMode {
    /** Never checks on its own, only when asked from the settings. */
    OFF,

    /** Downloads and installs a new release right away, behind a dialog that can't be closed. */
    AUTOMATIC,

    /** Shows a new release with its notes and lets the user pick what to do. */
    ASK,
}
