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

package me.knighthat.kreate3.source.youtube

import me.knighthat.kreate3.lyrics.FoundLyrics
import me.knighthat.kreate3.lyrics.LyricsQuery
import me.knighthat.kreate3.lyrics.LyricsSource
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeProvider

/**
 * The lyrics YouTube Music shows for its own songs, never timed. Songs of other origins have none
 * here.
 *
 * It lives in the app, which knows both the lyrics and the providers, so neither module has to
 * know the other.
 */
class YouTubeLyricsSource(private val youTube: YouTubeProvider) : LyricsSource {

    override val provider: LyricsProvider = LyricsProvider.YOUTUBE
    override val name: String = "YouTube Music"

    override suspend fun find(query: LyricsQuery): FoundLyrics? {
        if (query.origin != Origin.YOUTUBE) return null
        val lyrics = youTube.lyrics(query.songId) ?: return null
        return FoundLyrics(lyrics.text, LyricsSync.NONE, provider, lyrics.credit)
    }
}
