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

package me.knighthat.kreate.database.repository

import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserPlaylist
import kotlin.time.Instant

/**
 * An item of a server's library with what the active user did with it there.
 *
 * @property starredAt when the user starred it on the server, `null` when not starred
 */
data class RemoteItem<T>(val item: T, val starredAt: Instant?)

/**
 * A song of a server with what it points at and what the active user did with it there.
 *
 * @property album the song's album as the song knows it, `null` when it has none. It only fills
 * in an album that isn't stored yet.
 * @property artists every artist credited on the song, which only fill in the ones not stored yet
 * @property starredAt when the user starred it on the server, `null` when not starred
 */
data class RemoteSong(
    val song: Song,
    val album: Album?,
    val artists: List<Artist>,
    val starredAt: Instant?,
)

/**
 * Keeps the library of a server in the shared catalog and the active user's library. Each call
 * replaces what is stored for one kind of item from one [Origin] with what the server has now,
 * items of other origins are not touched. Ids are the server's own.
 *
 * Every call waits up to 10 seconds for an active user and throws [IllegalStateException] if there
 * is none.
 */
interface RemoteCatalogRepository {

    /**
     * Stores [albums] in the catalog and the active user's library. The user's own name, cover
     * and artists are kept, and so is the album's `url` when the server gives none, like a share
     * link made for it. The bookmark follows the star on the server, except that an album the
     * user disliked stays disliked while it isn't starred. Albums of [origin] that are no longer
     * on the server leave the user's library.
     *
     * @throws IllegalArgumentException when an album is from another origin
     */
    suspend fun syncAlbums(origin: Origin, albums: List<RemoteItem<Album>>)

    /**
     * Same as [syncAlbums] for artists, the star is the follow.
     */
    suspend fun syncArtists(origin: Origin, artists: List<RemoteItem<Artist>>)

    /**
     * Stores [playlists] as the active user's, matched by [UserPlaylist.remoteId]. Their name and
     * cover come from the server. Playlists of [origin] with a remote id that are no longer on
     * the server are deleted, playlists made in the app are kept.
     *
     * @throws IllegalArgumentException when a playlist has no remote id or is from another origin
     */
    suspend fun syncPlaylists(origin: Origin, playlists: List<UserPlaylist>)

    /**
     * Stores [songs] in the catalog and the active user's library, credited to their artists.
     * The like follows the star on the server like the bookmark of [syncAlbums]. [albums] and
     * [artists] are stored as with [syncAlbums] and [syncArtists], without removing anything.
     *
     * An album or artist a song points at that isn't stored yet is stored from what the song
     * knows, without a row in the user's library. One already stored is kept as it is.
     *
     * @param playlistId a playlist of the user whose songs become [songs], `null` for none
     * @throws IllegalArgumentException when an item is from another origin, or the playlist
     * doesn't exist or isn't the active user's
     */
    suspend fun syncSongs(
        origin: Origin,
        songs: List<RemoteSong>,
        albums: List<RemoteItem<Album>> = emptyList(),
        artists: List<RemoteItem<Artist>> = emptyList(),
        playlistId: Long? = null,
    )
}
