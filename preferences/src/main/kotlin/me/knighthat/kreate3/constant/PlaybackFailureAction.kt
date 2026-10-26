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
 * What the player does when a song fails to play, or loads for too long. Trying again happens
 * once per song.
 */
enum class PlaybackFailureAction(val retries: Boolean, val skips: Boolean) {
    /** Plays the next song. */
    SKIP(retries = false, skips = true),

    /** Loads the song again from where it was, then plays the next song if it fails again. */
    RETRY_THEN_SKIP(retries = true, skips = true),

    /** Loads the song again from where it was, then stops if it fails again. */
    RETRY_THEN_STOP(retries = true, skips = false),

    /** Stops on the song, playing again tries it again. */
    STOP(retries = false, skips = false),
}
