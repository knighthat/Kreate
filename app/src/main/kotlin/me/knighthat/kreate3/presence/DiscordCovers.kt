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

package me.knighthat.kreate3.presence

import me.knighthat.kreate3.preferences.Preferences
import kotlin.math.abs

/** Sizes of a cover uploaded for Discord, and the stops of their settings. */
internal object DiscordCovers {

    /** Stops of [Preferences.DISCORD_COVER_SIZE], in pixels. */
    val resolutions: IntProgression = 256..1536 step 256

    /** The stop of [Preferences.DISCORD_COVER_SIZE] its slider fills from, Discord's advice. */
    const val ADVISED_RESOLUTION = 1024

    /**
     * Stops of the file size settings, like [Preferences.DISCORD_COVER_BYTES_WIFI]: 128 KiB to
     * 1 MiB in units of 1024 when [binary], 100 kB to 1 MB otherwise.
     */
    fun sizes(binary: Boolean): List<Int> =
        if (binary) (1..8).map { it * 131_072 } else (1..10).map { it * 100_000 }

    /** The stop of [sizes] closest to [bytes]. */
    fun snap(bytes: Int, binary: Boolean): Int = sizes(binary).minBy { abs(it - bytes) }

    /**
     * Moves both file size settings to the closest stops of [binary]'s, for when
     * [Preferences.BINARY_SIZES] changes.
     */
    fun snapSettings(binary: Boolean) {
        listOf(Preferences.DISCORD_COVER_BYTES_WIFI, Preferences.DISCORD_COVER_BYTES_MOBILE)
            .forEach { it.update(snap(it.value, binary)) }
    }
}
