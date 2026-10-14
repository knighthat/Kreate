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

import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteSong

/**
 * A page of YouTube Music that isn't one of the kinds this provider knows, like the list behind
 * "See all". Open it with [YouTubeProvider.browse].
 */
data class YouTubeBrowse(val browseId: String, val params: String? = null)

/**
 * A part of a longer list.
 *
 * @property continuation hands out the next part to the method that gave this one, `null` when
 * this is the last part. It is only good for a while, don't keep it.
 */
data class YouTubePage<T>(val items: List<T>, val continuation: String?)

/**
 * Anything a list of YouTube Music can hold. A song is a [RemoteSong] that is never starred,
 * nobody is signed in.
 */
sealed interface YouTubeItem {

    /**
     * @property isVideo whether it is a music video or any other video instead of a song
     * published as audio
     */
    data class SongItem(val song: RemoteSong, val isVideo: Boolean = false) : YouTubeItem

    data class AlbumItem(val album: Album) : YouTubeItem

    data class ArtistItem(val artist: Artist) : YouTubeItem

    /** Its id on YouTube is [UserPlaylist.remoteId], it isn't saved. */
    data class PlaylistItem(val playlist: UserPlaylist) : YouTubeItem
}

/**
 * A row of items with a title, like "Quick picks" on the home page or "Singles & EPs" of an artist.
 *
 * @property strapline small text above the title, like "MORE FROM"
 * @property more the page with every item of the row, `null` when the row already holds all of them
 */
data class YouTubeSection(
    val title: String?,
    val items: List<YouTubeItem>,
    val strapline: String? = null,
    val more: YouTubeBrowse? = null,
)

/**
 * Kinds of items [YouTubeProvider.search] can be limited to.
 */
enum class YouTubeSearchFilter(internal val params: String) {
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
    ALBUMS("EgWKAQIYAWoKEAkQAxAEEAkQBQ%3D%3D"),
    ARTISTS("EgWKAQIgAWoKEAkQAxAEEAkQBQ%3D%3D"),

    /** Playlists made by YouTube Music. */
    FEATURED_PLAYLISTS("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"),

    /** Playlists made by users. */
    COMMUNITY_PLAYLISTS("EgeKAQQoAEABagwQDhAKEAMQBRAJEAQ%3D"),
}

/**
 * What YouTube Music suggests for a search being typed.
 *
 * @property queries whole searches that start like the text typed
 * @property items items it matched already
 */
data class YouTubeSuggestions(val queries: List<String>, val items: List<YouTubeItem>)

/**
 * @property songs the songs in the album's order, each with the album and its artists
 * @property playlistId id of the playlist that holds the album's songs, its radio is
 * [YouTubeProvider.upNext] of it
 * @property sections rows shown below the songs, like "Other versions"
 */
data class YouTubeAlbumPage(
    val album: Album,
    val artists: List<Artist>,
    val songs: List<RemoteSong>,
    val playlistId: String?,
    val description: String?,
    val sections: List<YouTubeSection>,
)

/**
 * @property topSongs the artist's most played songs, a few of them
 * @property songsPlaylistId id of the playlist with every song of the artist, open it with
 * [YouTubeProvider.playlistPage]
 * @property sections rows of the page, like "Albums", "Singles & EPs" and "Fans might also like".
 * Albums with no artist named on them are given the artist.
 */
data class YouTubeArtistPage(
    val artist: Artist,
    val description: String?,
    val topSongs: List<RemoteSong>,
    val songsPlaylistId: String?,
    val sections: List<YouTubeSection>,
)

/**
 * @property author who made the playlist, like "YouTube Music"
 * @property songs the first songs, in the playlist's order
 * @property continuation gives the next songs to [YouTubeProvider.playlistSongs], `null` when
 * [songs] are all of them
 */
data class YouTubePlaylistPage(
    val playlist: UserPlaylist,
    val author: String?,
    val description: String?,
    val songs: List<RemoteSong>,
    val continuation: String?,
)

/**
 * @property source who provides the lyrics, like "Source: Musixmatch", which should be shown
 * with them
 */
data class YouTubeLyrics(val lyrics: Lyrics, val source: String?)
