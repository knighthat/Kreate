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
 * What a quick action does with the songs of a list. Stored by name, for the button of album and
 * artist pages.
 */
enum class QueueAction {
    /** Replaces the queue with the songs, in the list's order, and plays them. */
    PLAY,

    /** Like [PLAY], in a random order. */
    SHUFFLE,

    /** Adds the songs to the end of the queue. */
    ENQUEUE,

    /** Adds the songs right after the song playing, in the list's order. */
    PLAY_NEXT,
}
