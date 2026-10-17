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

package me.knighthat.kreate3.database.repository

import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import kotlin.time.Instant

/**
 * An item of a server's library with what the active user did with it there. Albums, artists
 * and playlists of a server are only shown, never stored.
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
 * @property starredAt when the user starred it on the server, `null` when not starred or when the
 * server doesn't tell when
 */
data class RemoteSong(
    val song: Song,
    val album: Album?,
    val artists: List<Artist>,
    val starredAt: Instant?,
)

/**
 * Keeps the songs a server lists as liked, and the songs the user played, in the shared catalog
 * and the active user's library. Nothing else of a server's library is stored, items of other
 * origins are not touched. Ids are the server's own.
 *
 * Every call waits up to 10 seconds for an active user and throws [IllegalStateException] if there
 * is none.
 */
interface RemoteCatalogRepository {

    /**
     * Makes [songs], the songs the server lists as liked with the most recently liked first, the
     * active user's liked songs of [origin]. Each is stored with the server's values, credited to
     * its artists. An album or artist a song points at that isn't stored yet is stored from what
     * the song knows, without a row in the user's library, one already stored is kept as it is.
     *
     * A song is liked at its [RemoteSong.starredAt] when the server gives one. Otherwise a song
     * already liked keeps its time, and any other, new or not liked until now, is liked now, a
     * millisecond earlier for each song before it so they keep the server's order.
     *
     * When [complete], a song of [origin] liked in the user's library that isn't in [songs] loses
     * its like, the song stays in their library. Dislikes of songs not in [songs] always stay,
     * a server has no list of them.
     *
     * @param complete whether [songs] are all the server's liked songs, `false` when it has more
     * than were fetched
     * @throws IllegalArgumentException when an item is from another origin
     */
    suspend fun syncLikedSongs(origin: Origin, songs: List<RemoteSong>, complete: Boolean = true)

    /**
     * Keeps [song], which the user started playing, in the shared catalog and adds it to the
     * user's library, with its album and artists like [syncLikedSongs] does. The catalog takes
     * the source's values. A song already in the library keeps its row as it is, its like
     * included, and a new one isn't liked.
     *
     * @throws IllegalArgumentException when the song or what it points at is from the device
     */
    suspend fun savePlayedSong(song: RemoteSong)
}
