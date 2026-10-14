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

package me.knighthat.kreate3.provider.youtube

import android.net.Uri
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.SearchResults

/**
 * YouTube. Nothing is implemented, the backend library is not picked yet.
 */
class YouTubeProvider : Provider {

    override val origin = Origin.YOUTUBE

    override suspend fun search(query: String, limit: Int, offset: Int): SearchResults =
        TODO(PENDING)

    override suspend fun song(songId: String): Song? = TODO(PENDING)

    override suspend fun album(albumId: String): Album? = TODO(PENDING)

    override suspend fun artist(artistId: String): Artist? = TODO(PENDING)

    override suspend fun songsOf(albumId: String): List<Song> = TODO(PENDING)

    override suspend fun albumsOf(artistId: String): List<Album> = TODO(PENDING)

    override suspend fun playbackUri(songId: String): Uri? = TODO(PENDING)

    override fun artworkUri(artworkId: String, size: Int?): Uri? = TODO(PENDING)

    private companion object {
        const val PENDING = "Waiting for the YouTube backend library"
    }
}
