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
 * Whether the YouTube songs to play are found in the signed in session. Only takes effect while
 * someone is signed in and the YouTube account is on.
 */
enum class PlaybackLogin {
    /** Songs are asked signed out, the ones that need a login (age limits) can't be played. */
    NEVER,

    /** Songs are asked signed out, a song that needs a login is asked again in the session. */
    ON_FAILURE,

    /** Every song is asked in the session. */
    ALWAYS,
}
