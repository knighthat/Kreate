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
 * What one of the two buttons under a playlist's header does, see
 * `Preferences.PLAYLIST_FIRST_BUTTON`. Stored by name.
 */
enum class PlaylistButton {
    /** Plays the songs in the playlist's order. */
    PLAY,

    /** Plays the songs in a random order. */
    SHUFFLE,

    /** Adds the songs right after the song playing. */
    PLAY_NEXT,

    /** Adds the songs to the end of the queue. */
    ENQUEUE,

    /** Shares a link to the playlist. */
    SHARE,
}
