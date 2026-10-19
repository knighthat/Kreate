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
 * How good the audio of a streamed song is, the better the more data it takes. A source gives
 * the nearest it has, one that has nothing better gives the same for several of them.
 */
enum class StreamQuality {
    /** The smallest the source has, about 48 kbps. */
    LOW,

    /** About 128 kbps. */
    MEDIUM,

    /** The best the source has for everyone, about 160 kbps on YouTube. */
    HIGH,

    /**
     * The best the source has, which may need an account: about 256 kbps on YouTube with Premium.
     * Without one it is the same as [HIGH].
     */
    ORIGINAL,
}
