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
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.SearchResults
import me.knighthat.kreate3.provider.youtube.InnerTube.Endpoint
import me.knighthat.kreate3.provider.youtube.YouTubeItem.AlbumItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.ArtistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.PlaylistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.SongItem
import me.knighthat.kreate3.provider.youtube.YouTubeProvider.Companion.LIBRARY_PAGES
import me.knighthat.kreate3.provider.youtube.YouTubeProvider.Companion.PLAYLIST_PAGES
import me.knighthat.kreate3.provider.youtube.YouTubeProvider.Companion.SEARCH_PAGES
import java.util.Locale

/**
 * Whether [cookies], a `Cookie` header of youtube.com, are of a signed in session, like the ones
 * a sign in page leaves behind.
 */
fun isYouTubeSession(cookies: String): Boolean = YouTubeSession.of(cookies) != null

/**
 * YouTube Music, read through InnerTube: searching, songs, albums, artists, playlists, the home
 * page, radios, lyrics and related items. What to play is not fetched, [playbackUri] is always
 * `null`.
 *
 * Nobody needs to be signed in. Once the user signed in, with the cookies kept in
 * `Secrets.YOUTUBE_COOKIES`, and while the account is in use, every request is sent in their
 * session and the user's library can be read, see [libraryAlbums].
 *
 * Lists that are long come in parts, a [YouTubePage] hands its continuation to the method that
 * gave it for the next part. Pictures are the addresses YouTube gives, [artworkUri] sizes them.
 *
 * Every request may throw [YouTubeException]. A page YouTube doesn't have is `null` instead.
 */
class YouTubeProvider internal constructor(
    private val innerTube: InnerTube,
    private val parser: YouTubeParser,
) : Provider {

    /**
     * @param client the app's client
     * @param cookies the cookies of the signed in session, empty while nobody is signed in. They
     * are the active profile's by default, none while [Preferences.YOUTUBE_ACCOUNT_ENABLED] is off.
     * @param pageId page id of the channel the signed in user acts as, empty for their account's
     * own. The active profile's choice by default.
     */
    constructor(
        client: HttpClient,
        json: Json,
        cookies: suspend () -> String = {
            if (Preferences.YOUTUBE_ACCOUNT_ENABLED.value) Secrets.YOUTUBE_COOKIES.current() else ""
        },
        pageId: () -> String = { Preferences.YOUTUBE_PAGE_ID.value },
    ) : this(
        InnerTube(client, json, Locale::getDefault, cookies, pageId = pageId),
        YouTubeParser { it.toUri() },
    )

    override val origin = Origin.YOUTUBE

    /**
     * Whether cookies of a signed in session are saved. They may still belong to a session that
     * ended, [account] finds out.
     */
    suspend fun isSignedIn(): Boolean = innerTube.session() != null

    /**
     * Something that tells the saved session apart from others, `null` while nobody is signed in.
     * Another channel of the same account is another session. It is derived from the cookies,
     * keep it out of logs too.
     */
    suspend fun sessionId(): String? = innerTube.session()?.id

    /**
     * Every channel of the signed in user's Google account, in the order YouTube lists them. An
     * account with brand accounts has several, see [YouTubeChannel.pageId].
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun channels(): List<YouTubeChannel>? {
        if (!isSignedIn()) return null
        return parser.channels(innerTube.accountsList())
    }

    /**
     * The signed in user's account.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun account(): YouTubeAccount? {
        if (!isSignedIn()) return null
        return parser.account(innerTube.accountMenu())
            ?: throw YouTubeException("YouTube didn't say who is signed in", signedOut = true)
    }

    /**
     * The albums the user saved to their library, most recently added first.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryAlbums(): List<Album>? =
        library(LIBRARY_ALBUMS)?.filterIsInstance<AlbumItem>()?.map { it.album }
            ?.distinctBy { it.id }

    /**
     * The artists the user subscribed to.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryArtists(): List<Artist>? =
        library(LIBRARY_ARTISTS)?.filterIsInstance<ArtistItem>()?.map { it.artist }
            ?.distinctBy { it.id }

    /**
     * The playlists in the user's library, theirs and the ones they saved, with "Liked Music"
     * whose id is `LM`. Their ids on YouTube are [UserPlaylist.remoteId].
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryPlaylists(): List<UserPlaylist>? =
        library(LIBRARY_PLAYLISTS)?.filterIsInstance<PlaylistItem>()?.map { it.playlist }
            ?.distinctBy { it.remoteId }

    /**
     * The songs the user liked, "Liked Music", most recently liked first, fetched part after part
     * up to [PLAYLIST_PAGES] parts of about 100 songs. YouTube doesn't tell when they were liked.
     *
     * @return the songs, with a continuation when more are liked than were fetched, or `null`
     * while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun likedSongs(): YouTubePage<RemoteSong>? {
        if (!isSignedIn()) return null

        // An answer outside the session fails, it would like nothing and take every like away
        val page = parser.playlistPage(
            LIKED_SONGS,
            innerTube.browse(PLAYLIST_BROWSE_PREFIX + LIKED_SONGS, signedIn = true),
        ) ?: throw YouTubeException("YouTube didn't list the liked songs")
        val songs = page.songs.toMutableList()
        var continuation = page.continuation
        var pages = 1
        while (continuation != null && pages < PLAYLIST_PAGES) {
            val next = parser.playlistSongs(
                innerTube.continuation(Endpoint.BROWSE, continuation, signedIn = true)
            )
            songs += next.items
            continuation = next.continuation
            pages++
        }
        return YouTubePage(songs.distinctBy { it.song.id }, continuation)
    }

    /**
     * Every item of a page of the user's library, part after part up to [LIBRARY_PAGES] parts.
     * An answer outside the session fails, it would list nothing and empty the library.
     *
     * @return `null` while nobody is signed in
     */
    private suspend fun library(browseId: String): List<YouTubeItem>? {
        if (!isSignedIn()) return null

        var page = parser.browsePage(innerTube.browse(browseId, signedIn = true))
        val items = page.items.toMutableList()
        var pages = 1
        while (pages < LIBRARY_PAGES) {
            val token = page.continuation ?: break
            page =
                parser.browsePage(innerTube.continuation(Endpoint.BROWSE, token, signedIn = true))
            items += page.items
            pages++
        }
        return items
    }

    /**
     * One kind of results of a search, best matches first.
     *
     * @param continuation the token of the previous page, `null` for the first one
     */
    suspend fun search(
        query: String,
        filter: YouTubeSearchFilter,
        continuation: String? = null,
    ): YouTubePage<YouTubeItem> = parser.searchPage(
        if (continuation == null) innerTube.search(query, filter.params)
        else innerTube.continuation(Endpoint.SEARCH, continuation)
    )

    /**
     * Songs, albums and artists, searched at once. YouTube gives 20 results of a kind per
     * request, a kind is asked again only while fewer than [offset] + [limit] arrived, and at most
     * [SEARCH_PAGES] times: the default limit gives up to 60 of each.
     */
    override suspend fun search(query: String, limit: Int, offset: Int): SearchResults {
        if (query.isBlank() || limit <= 0) return SearchResults.EMPTY
        val wanted = offset.toLong() + limit

        val (songs, albums, artists) = coroutineScope {
            listOf(
                YouTubeSearchFilter.SONGS,
                YouTubeSearchFilter.ALBUMS,
                YouTubeSearchFilter.ARTISTS
            )
                .map { async { collect(query, it, wanted) } }
                .awaitAll()
        }
        fun <T> List<T>.window() = drop(offset).take(limit)

        return SearchResults(
            songs = songs.filterIsInstance<SongItem>().map { it.song.song }.window(),
            albums = albums.filterIsInstance<AlbumItem>().map { it.album }.window(),
            artists = artists.filterIsInstance<ArtistItem>().map { it.artist }.window(),
        )
    }

    private suspend fun collect(
        query: String,
        filter: YouTubeSearchFilter,
        wanted: Long
    ): List<YouTubeItem> {
        val items = mutableListOf<YouTubeItem>()
        var page = search(query, filter)
        var pages = 1
        items += page.items
        while (items.size < wanted && pages < SEARCH_PAGES) {
            page = search(query, filter, page.continuation ?: break)
            items += page.items
            pages++
        }
        return items
    }

    /** What YouTube Music suggests while [input] is being typed. */
    suspend fun suggestions(input: String): YouTubeSuggestions =
        if (input.isBlank()) YouTubeSuggestions(emptyList(), emptyList())
        else parser.suggestions(innerTube.suggestions(input))

    /**
     * The song with its album and artists.
     *
     * @return `null` when YouTube doesn't know the song
     */
    suspend fun songDetails(songId: String): RemoteSong? =
        parser.queue(innerTube.next(songId, null)).items
            .firstOrNull { it.song.song.id == songId }?.song

    /** @return `null` when YouTube doesn't know the album */
    suspend fun albumPage(albumId: String): YouTubeAlbumPage? =
        parser.albumPage(albumId, innerTube.browse(albumId))

    /** @return `null` when YouTube doesn't know the artist */
    suspend fun artistPage(artistId: String): YouTubeArtistPage? =
        parser.artistPage(artistId, innerTube.browse(artistId))

    /**
     * The playlist with its first songs, [playlistSongs] gives the next ones.
     *
     * @param playlistId id of the playlist, without `VL`
     * @return `null` when YouTube doesn't know the playlist
     */
    suspend fun playlistPage(playlistId: String): YouTubePlaylistPage? =
        parser.playlistPage(playlistId, innerTube.browse(PLAYLIST_BROWSE_PREFIX + playlistId))

    /**
     * The next songs of a playlist.
     *
     * @param continuation of [YouTubePlaylistPage] or of the previous call
     */
    suspend fun playlistSongs(continuation: String): YouTubePage<RemoteSong> =
        parser.playlistSongs(innerTube.continuation(Endpoint.BROWSE, continuation))

    /**
     * Every song of a playlist, in its order, fetched part after part up to [PLAYLIST_PAGES]
     * parts of about 100 songs.
     *
     * @return `null` when YouTube doesn't know the playlist
     */
    suspend fun allPlaylistSongs(playlistId: String): List<RemoteSong>? {
        val page = playlistPage(playlistId) ?: return null
        val songs = page.songs.toMutableList()
        var continuation = page.continuation
        var pages = 1
        while (continuation != null && pages < PLAYLIST_PAGES) {
            val next = playlistSongs(continuation)
            songs += next.items
            continuation = next.continuation
            pages++
        }
        return songs
    }

    /**
     * A page of any kind, like the list behind "See all" of a [YouTubeSection], or its next part.
     *
     * @param continuation the token of the previous part, `null` for the first one
     */
    suspend fun browse(
        endpoint: YouTubeBrowse,
        continuation: String? = null
    ): YouTubePage<YouTubeItem> =
        parser.browsePage(
            if (continuation == null) innerTube.browse(endpoint.browseId, endpoint.params)
            else innerTube.continuation(Endpoint.BROWSE, continuation)
        )

    /**
     * The rows of the home page, the same for everyone in a country as nobody is signed in.
     *
     * @param continuation the token of the previous rows, `null` for the first ones
     */
    suspend fun home(continuation: String? = null): YouTubePage<YouTubeSection> =
        parser.homePage(
            if (continuation == null) innerTube.browse(HOME)
            else innerTube.continuation(Endpoint.BROWSE, continuation)
        )

    /**
     * The songs YouTube Music plays after [songId] or the songs of [playlistId], like the radio of
     * an album with the album's [YouTubeAlbumPage.playlistId]. The first song is the one asked.
     *
     * @param continuation the token of the previous songs, `null` for the first ones. The same
     * [songId] and [playlistId] must be given with it.
     */
    suspend fun upNext(
        songId: String?,
        playlistId: String?,
        continuation: String? = null,
    ): YouTubePage<SongItem> {
        require(songId != null || playlistId != null) { "A queue starts from a song or a playlist" }
        return parser.queue(
            if (continuation == null) innerTube.next(songId, playlistId)
            else innerTube.continuation(Endpoint.NEXT, continuation, playlistId)
        )
    }

    /** Endless songs like [songId], starting with it. */
    suspend fun radio(songId: String, continuation: String? = null): YouTubePage<SongItem> =
        upNext(songId, RADIO_PREFIX + songId, continuation)

    /** @return `null` when YouTube has no lyrics for the song */
    suspend fun lyrics(songId: String): YouTubeLyrics? {
        val browseId =
            parser.tabBrowseId(innerTube.next(songId, null), LYRICS_PREFIX) ?: return null
        return parser.lyrics(songId, innerTube.browse(browseId))
    }

    /** Rows of songs, playlists, artists and albums like [songId]. */
    suspend fun related(songId: String): List<YouTubeSection> {
        val browseId =
            parser.tabBrowseId(innerTube.next(songId, null), RELATED_PREFIX) ?: return emptyList()
        return parser.sections(innerTube.browse(browseId))
    }

    override suspend fun song(songId: String): Song? = songDetails(songId)?.song

    override suspend fun album(albumId: String): Album? = albumPage(albumId)?.album

    override suspend fun artist(artistId: String): Artist? = artistPage(artistId)?.artist

    override suspend fun songsOf(albumId: String): List<Song> =
        albumPage(albumId)?.songs?.map { it.song }.orEmpty()

    /**
     * Every album, single and EP of the artist. A row of the artist's page that has more than it
     * shows is opened, a few at a time.
     */
    override suspend fun albumsOf(artistId: String): List<Album> {
        val page = artistPage(artistId) ?: return emptyList()
        val name = page.artist.name

        return coroutineScope {
            page.sections.map { section ->
                async {
                    val more = section.more?.takeIf { it.browseId.startsWith(DISCOGRAPHY_PREFIX) }
                    if (more == null) section.items
                    else browse(more).items.map { it.creditedTo(name) }
                }
            }.awaitAll()
        }.flatten().filterIsInstance<AlbumItem>().map { it.album }.distinctBy { it.id }
    }

    /** Not fetched yet, nothing from YouTube can be played. */
    override suspend fun playbackUri(songId: String): Uri? = null

    /**
     * @param artworkId the address of a picture as YouTube gave it, like [Song.thumbnail]
     */
    override fun artworkUri(artworkId: String, size: Int?): Uri? =
        artworkId.takeIf { it.isNotBlank() }?.let { resizeArtwork(it, size).toUri() }

    private companion object {
        const val HOME = "FEmusic_home"

        /** Playlist id of the radio of a song is this followed by the song's id. */
        const val RADIO_PREFIX = "RDAMVM"

        /** Requests a search of one kind may send for [Provider.search]. */
        const val SEARCH_PAGES = 3

        /** Requests [allPlaylistSongs] may send, about 100 songs each. */
        const val PLAYLIST_PAGES = 50

        /** Id of the "Liked Music" playlist, the same for everyone. */
        const val LIKED_SONGS = "LM"

        const val LIBRARY_ALBUMS = "FEmusic_liked_albums"
        const val LIBRARY_ARTISTS = "FEmusic_library_corpus_artists"
        const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"

        /** Requests a page of the library may send, about 25 to 100 items each. */
        const val LIBRARY_PAGES = 40
    }
}
