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

package me.knighthat.kreate3.model

/**
 * Where lyrics are fetched from. The database and the settings store the [name], so never rename
 * a value.
 *
 * The order is the one sources are asked in when every source is searched: the ones that time
 * lyrics per word first, so a search stops sooner.
 */
enum class LyricsProvider {
    /** The server of the Better Lyrics extension: Apple Music's lyrics, mostly per word. */
    BETTER_LYRICS,

    /** LyricsPlus, the server of the YouLy+ extension: mostly per word. */
    LYRICS_PLUS,

    /** LRCLIB, a free database of lyrics: plain or per line. */
    LRCLIB,

    /** YouTube Music, for its own songs: plain. */
    YOUTUBE,
}
