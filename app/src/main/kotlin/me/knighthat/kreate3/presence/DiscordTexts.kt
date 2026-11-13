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

/**
 * What the presence reads on Discord. English on purpose, never string resources: Discord shows
 * them as they are to everyone who looks at the profile, whatever their language. Button labels
 * stay within Discord's 32 characters.
 */
internal object DiscordTexts {

    const val APP_NAME = "Kreate"
    const val YOUTUBE_MUSIC = "YouTube Music"

    const val PAUSED = "Paused"
    const val VIEWING_ALBUM = "Viewing an album"
    const val VIEWING_ARTIST = "Viewing an artist"
    const val VIEWING_PLAYLIST = "Viewing a playlist"
    const val SEARCHING = "Searching"
    const val GET_APP = "Get $APP_NAME"
    const val PLAY_ON_YOUTUBE_MUSIC = "Play on $YOUTUBE_MUSIC"
    const val VIEW_ON_YOUTUBE_MUSIC = "View on $YOUTUBE_MUSIC"

    /** Second line of a paused song by [artists]. */
    fun paused(artists: String): String = "$artists · $PAUSED"

    /** Second line of a search for [query]. */
    fun quoted(query: String): String = "\"$query\""
}
