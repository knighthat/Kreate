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

package me.knighthat.kreate3.provider.local

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.repository.AlbumRepository
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.SearchResults

private val ALBUM_ART: Uri = "content://media/external/audio/albumart".toUri()

/**
 * Songs found on the device. The scanner keeps the catalog up to date, so what this provider
 * knows is read back from the database.
 */
class LocalProvider(
    private val songs: SongRepository,
    private val albums: AlbumRepository,
    private val artists: ArtistRepository,
) : Provider {

    override val origin = Origin.LOCAL

    override suspend fun search(query: String, limit: Int, offset: Int): SearchResults =
        TODO("Local search is paged by the app, move it here")

    override suspend fun song(songId: String): Song? = songs.find(songId).first()

    override suspend fun album(albumId: String): Album? = albums.find(albumId).first()

    override suspend fun artist(artistId: String): Artist? = artists.find(artistId).first()

    override suspend fun songsOf(albumId: String): List<Song> =
        TODO("Album songs are paged by the app, move it here")

    override suspend fun albumsOf(artistId: String): List<Album> =
        TODO("Artist albums are paged by the app, move it here")

    override suspend fun playbackUri(songId: String): Uri? =
        songId.toLongOrNull()?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        }

    /** @param artworkId id of the album in the media index, it has no other sizes */
    override fun artworkUri(artworkId: String, size: Int?): Uri? =
        artworkId.toLongOrNull()?.let { ContentUris.withAppendedId(ALBUM_ART, it) }
}
