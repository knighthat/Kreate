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

package me.knighthat.kreate3.provider

import android.net.Uri
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song

/**
 * Where music comes from. Every [Origin] has exactly one implementation, find it with [Providers].
 *
 * A provider answers with the models of the database but never writes to it, the caller decides
 * what to keep. Ids are the ones of the provider, the same as `id` on the models.
 */
interface Provider {

    /** The origin this provider serves, stored on every item it returns. */
    val origin: Origin

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT, offset: Int = 0): SearchResults

    /** @return `null` when the provider doesn't know the song */
    suspend fun song(songId: String): Song?

    /** @return `null` when the provider doesn't know the album */
    suspend fun album(albumId: String): Album?

    /** @return `null` when the provider doesn't know the artist */
    suspend fun artist(artistId: String): Artist?

    /** Songs of the album, in the order of the album. */
    suspend fun songsOf(albumId: String): List<Song>

    suspend fun albumsOf(artistId: String): List<Album>

    /**
     * Address the player can stream or read the song from. It may expire, ask again instead of
     * keeping it.
     *
     * @return `null` when the song can't be played
     */
    suspend fun playbackUri(songId: String): Uri?

    /**
     * Address of a cover or an artist picture, built from the [artworkId] the provider gave.
     *
     * @param size edge in pixels the picture is needed at, `null` for the original
     * @return `null` when there is no picture
     */
    fun artworkUri(artworkId: String, size: Int? = null): Uri?

    /**
     * Addresses the cover of a song may be at, worked out from its ids alone, the most fitting
     * first. For when the address a list gave is missing or doesn't load, the source may have no
     * picture at any of them. None by default.
     *
     * @param albumId album of the song, `null` when it has none or it isn't known
     * @param size like for [artworkUri]
     */
    fun songArtworkUris(songId: String, albumId: String?, size: Int? = null): List<Uri> =
        emptyList()

    companion object {
        const val DEFAULT_LIMIT = Int.MAX_VALUE
    }
}
