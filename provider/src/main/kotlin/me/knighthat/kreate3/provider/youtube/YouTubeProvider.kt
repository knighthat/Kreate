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

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.HttpClientFactory
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.SearchResults
import me.knighthat.kreate3.provider.youtube.InnerTube.Endpoint
import me.knighthat.kreate3.provider.youtube.YouTubeItem.AlbumItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.ArtistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.PlaylistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.SongItem
import okhttp3.Call
import okhttp3.OkHttpClient
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Whether [cookies], a `Cookie` header of youtube.com, are of a signed in session, like the ones
 * a sign in page leaves behind.
 */
fun isYouTubeSession(cookies: String): Boolean = YouTubeSession.of(cookies) != null

/**
 * YouTube Music, read through InnerTube: searching, songs, albums, artists, playlists, the home
 * page, radios, lyrics, related items and what to play, see [stream].
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
    private val clients: YouTubeClients? = null,
) : Provider {

    /**
     * Sends every request with [client], without a proxy.
     *
     * @param cookies the cookies of the signed in session, empty while nobody is signed in. They
     * are the active profile's by default, none while [Preferences.YOUTUBE_ACCOUNT_ENABLED] is off.
     * @param pageId page id of the channel the signed in user acts as, empty for their account's
     * own. The active profile's choice by default.
     * @param context runs the WebView that makes PO tokens, `null` plays without them
     * @param region the country sent as `gl`, [youTubeRegion] by default
     */
    constructor(
        client: HttpClient,
        json: Json,
        context: Context? = null,
        cookies: suspend () -> String = ::profileCookies,
        pageId: suspend () -> String = ::profilePageId,
        region: () -> String = ::youTubeRegion,
    ) : this(
        innerTube({ client }, json, context, cookies, pageId, region),
        YouTubeParser { it.toUri() },
    )

    /**
     * Sends every request with a client derived from [base], through the active profile's proxy,
     * see [youTubeProxy], in the active profile's session.
     *
     * @param base the app's client
     * @param factory sets up the Ktor clients like the app's
     * @param context runs the WebView that makes PO tokens, `null` plays without them
     * @param cookies the cookies of the signed in session, the active profile's while its YouTube
     * account is on by default; the setup wizard gives its own, nothing is enabled yet there
     */
    constructor(
        base: OkHttpClient,
        factory: HttpClientFactory,
        json: Json,
        context: Context?,
        cookies: suspend () -> String = ::profileCookies,
    ) : this(YouTubeClients(base, factory), json, context, cookies)

    private constructor(
        clients: YouTubeClients,
        json: Json,
        context: Context?,
        cookies: suspend () -> String,
    ) : this(
        innerTube(clients::current, json, context, cookies, ::profilePageId),
        YouTubeParser { it.toUri() },
        clients,
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
        library(LIBRARY_ALBUMS)?.items?.filterIsInstance<AlbumItem>()?.map { it.album }
            ?.distinctBy { it.id }

    /**
     * The ids of the albums the user saved to their library.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryAlbumIds(): LibraryIds? =
        library(LIBRARY_ALBUMS)?.let { library ->
            LibraryIds(
                ids = library.items.filterIsInstance<AlbumItem>().map { it.album.id }.toSet(),
                complete = library.complete,
            )
        }

    /**
     * The artists the user subscribed to.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryArtists(): List<Artist>? =
        library(LIBRARY_ARTISTS)?.items?.filterIsInstance<ArtistItem>()?.map { it.artist }
            ?.distinctBy { it.id }

    /**
     * The ids of the artists the user subscribed to.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryArtistIds(): LibraryIds? =
        library(LIBRARY_ARTISTS)?.let { library ->
            LibraryIds(
                ids = library.items.filterIsInstance<ArtistItem>().map { it.artist.id }.toSet(),
                complete = library.complete,
            )
        }

    /**
     * The playlists in the user's library, theirs and the ones they saved, with "Liked Music"
     * whose id is `LM`. Their ids on YouTube are [UserPlaylist.remoteId].
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException with [YouTubeException.signedOut] when the session ended
     */
    suspend fun libraryPlaylists(): List<UserPlaylist>? =
        library(LIBRARY_PLAYLISTS)?.items?.filterIsInstance<PlaylistItem>()?.map { it.playlist }
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
     * Every item of a page of the user's library, part after part up to [LIBRARY_PAGES] parts,
     * and whether that was all of it.
     * An answer outside the session fails, it would list nothing and empty the library.
     *
     * @return `null` while nobody is signed in
     */
    private suspend fun library(browseId: String): LibraryItems? {
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
        return LibraryItems(items, complete = page.continuation == null)
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
        if (continuation == null)
            innerTube.search(query, filter.params)
        else
            innerTube.continuation(Endpoint.SEARCH, continuation)
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
        if (input.isBlank())
            YouTubeSuggestions(emptyList(), emptyList())
        else
            parser.suggestions(innerTube.suggestions(input))

    /**
     * The song with its album and artists.
     *
     * @return `null` when YouTube doesn't know the song
     */
    suspend fun songDetails(songId: String): RemoteSong? =
        parser.queue(innerTube.next(songId, null)).items
            .firstOrNull { it.song.song.id == songId }?.song

    /**
     * The details of [songIds], at most a hundred at a time. Songs YouTube doesn't know are left
     * out, in the order of [songIds]. One unknown id fails the request it is in (404, or 500), so a
     * failed request is split in halves until the unknown ones are alone.
     *
     * @throws YouTubeException when YouTube can't be reached or refuses for another reason
     */
    suspend fun songDetailsOf(songIds: List<String>): List<RemoteSong> {
        val ids = songIds.distinct()
        if (ids.isEmpty()) return emptyList()

        return try {
            parser.queueOfSongs(innerTube.queueOf(ids))
        } catch (e: YouTubeException) {
            if (e.status != NOT_FOUND && e.status != INTERNAL_ERROR) throw e
            if (ids.size == 1) return emptyList()

            val half = ids.size / 2
            songDetailsOf(ids.take(half)) + songDetailsOf(ids.drop(half))
        }
    }

    /**
     * What YouTube has for each of [songIds], asked with [InnerTube.queueOf] in batches of
     * [LOOK_UP_BATCH] one after the other (471 at once worked, 2026-11-13). A batch is never split:
     * finding which ids YouTube refuses would take a request after request. When YouTube refuses a
     * batch (an HTTP error) and the request carried no visitor id, a visitor id is asked for and
     * the batch sent once more. A batch refused again makes the whole answer `null`, the batches
     * after it aren't sent. One id YouTube doesn't know (404, or 500) or won't show signed out
     * (403, like a private video) refuses its whole batch.
     *
     * Entries are paired with the ids by position when the answer has one for each: an entry
     * carrying another id than the one asked (seen: `XauFHDC8__E` answered as `vr-7KMTnuFU`)
     * makes the asked one [SongLookup.Unplayable]. Otherwise they are paired by id, ids without
     * an entry are left out.
     *
     * @return `null` when YouTube refused a batch
     * @throws YouTubeException when YouTube can't be reached
     */
    suspend fun lookUpSongs(songIds: List<String>): Map<String, SongLookup>? {
        val found = HashMap<String, SongLookup>()
        for (batch in songIds.distinct().chunked(LOOK_UP_BATCH))
            found += lookUpBatch(batch) ?: return null
        return found
    }

    /** One batch of [lookUpSongs], `null` when YouTube refused it. */
    private suspend fun lookUpBatch(ids: List<String>): Map<String, SongLookup>? {
        val response = try {
            innerTube.queueOf(ids)
        } catch (e: YouTubeException) {
            e.status ?: throw e
            if (innerTube.hasVisitor()) return null
            try {
                innerTube.requestVisitor()
                innerTube.queueOf(ids)
            } catch (again: YouTubeException) {
                again.status ?: throw again
                return null
            }
        }
        val entries = parser.queueLookups(response)

        if (entries.size == ids.size)
            return ids.zip(entries).associate { (asked, entry) ->
                asked to if (entry.first == asked) entry.second else SongLookup.Unplayable
            }
        val byId = entries.toMap()
        return ids.mapNotNull { id -> byId[id]?.let { id to it } }.toMap()
    }

    /**
     * Whether YouTube still plays [songId], asked with one [InnerTube.queueOf] request. A song
     * YouTube took down, or published again under another id, is in the answer as unplayable
     * ("[Video blocked in country]"), an id it doesn't know fails it (404, or 500): neither plays.
     *
     * @throws YouTubeException when YouTube can't be reached or refuses for another reason
     */
    suspend fun isPlayable(songId: String): Boolean {
        val response = try {
            innerTube.queueOf(listOf(songId))
        } catch (e: YouTubeException) {
            if (e.status != NOT_FOUND && e.status != INTERNAL_ERROR) throw e
            Logger.i(tag = TAG) { "The queue doesn't know $songId (${e.status}), it doesn't play" }
            return false
        }
        val plays = parser.playsInQueue(response, songId)
        Logger.i(tag = TAG) {
            if (plays)
                "The queue has $songId as a song that plays"
            else
                "The queue has $songId as unplayable, or not at all"
        }
        return plays
    }

    /**
     * The song YouTube published in place of [songId], which no longer plays (see [isPlayable]),
     * found with one search of [title] and [artists] among songs, hyphens left out of the words
     * searched (YouTube takes "-word" as "without word"). Albums and artists are often changed on
     * re-publishing, so the length decides: a result fits when it is as long as [duration] give or
     * take [REPLACEMENT_LENGTH_SLACK] and its title shares words with [title], at least half of the
     * shorter title's (see [TitleMatch]). Titles are compared [folded], so "sister's noise (Version
     * 2022)" is the same as "sister's noise -version 2022-". Of those that fit, the closest title
     * goes first, then the nearest length, then one credited to one of [artistIds], then YouTube's
     * order.
     *
     * @param artists the artists' names as shown, only searched with
     * @param artistIds only break ties, may be empty
     * @return `null` when no result fits
     * @throws YouTubeException when YouTube can't be reached or refuses
     */
    suspend fun replacementOf(
        songId: String,
        title: String,
        artists: String?,
        artistIds: Collection<String>,
        duration: Duration,
    ): SongItem? {
        val words = folded(title)
        if (words.isEmpty()) {
            Logger.i(tag = TAG) { "No search for the song in place of $songId: no words in its title" }
            return null
        }

        // A word starting with "-" leaves results with it out, "LEVEL5 -judgelight-" finds nothing
        val query = listOfNotNull(title, artists?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .replace('-', ' ')
        val results = search(query, YouTubeSearchFilter.SONGS).items.filterIsInstance<SongItem>()
        Logger.d(tag = TAG) {
            "Searched for the song in place of $songId (${duration.inWholeSeconds} s, " +
                    "${artistIds.size} artist ids): ${results.size} songs"
        }

        class Fit(val item: SongItem, val title: TitleMatch, val off: Duration, val credited: Boolean)

        val fitting = results.mapNotNull { item ->
            val song = item.song.song
            val match = song.name?.let { titleMatch(title, words, it) }
            val off = song.duration?.let { (it - duration).absoluteValue }
            val credited = item.song.artists.any { it.id in artistIds }
            val misses = listOfNotNull(
                "the same id".takeIf { song.id == songId },
                "no length".takeIf { off == null },
                "${off?.inWholeSeconds} s off".takeIf { off != null && off > REPLACEMENT_LENGTH_SLACK },
                "another title".takeIf { match == null },
            )
            Logger.d(tag = TAG) {
                if (misses.isEmpty()) {
                    "${song.id} fits: title ${match!!.text}, ${off!!.inWholeSeconds} s off, " +
                            if (credited) "same artist" else "other artists"
                } else {
                    "${song.id} doesn't fit: ${misses.joinToString()}"
                }
            }
            if (misses.isEmpty()) Fit(item, match!!, off!!, credited) else null
        }
        // Sorting keeps the order of equal ones, YouTube's
        val found = fitting
            .sortedWith(compareBy({ it.title }, { it.off }, { !it.credited }))
            .firstOrNull()
            ?.item
        Logger.i(tag = TAG) {
            if (found == null)
                "No song fits in place of $songId among ${results.size} results"
            else
                "${found.song.song.id} is the song in place of $songId, of ${fitting.size} that fit"
        }
        return found
    }

    /**
     * The YouTube song that is [title] by [artists], [duration] long, like a track of a playlist
     * of another service, found with one search of [title] and [artists] among songs, hyphens left
     * out like in [replacementOf]. A result fits when it is as long as [duration] give or take
     * [REPLACEMENT_LENGTH_SLACK] and its title shares words with [title] (see [TitleMatch]); one
     * credited to none of [artists] fits only with the same title, folded, since names are often
     * written another way (in another script, or as a band and its members). A result naming
     * another version than [title] doesn't fit: one of [VERSION_WORDS] in one title only
     * ("Horizon (Instrumental)" isn't "Horizon (ft. Notelle)"). Of those that fit, one credited
     * to one of [artists] goes first, then the closest title, the nearest length, then YouTube's
     * order. Titles and artists are compared [folded], a name holding another's words is it
     * ("Camo & Krooked" is "Camo").
     *
     * @return `null` when no result fits, or [title] has no words (nothing is searched then)
     * @throws YouTubeException when YouTube can't be reached or refuses
     */
    suspend fun songMatching(title: String, artists: List<String>, duration: Duration): SongItem? {
        val words = folded(title)
        if (words.isEmpty()) {
            Logger.i(tag = TAG) { "No search for a song to match: no words in its title" }
            return null
        }

        val query = (listOf(title) + artists.filter { it.isNotBlank() })
            .joinToString(" ")
            .replace('-', ' ')
        val results = search(query, YouTubeSearchFilter.SONGS).items.filterIsInstance<SongItem>()
        val names = artists.map(::folded).filter { it.isNotEmpty() }
        val versions = words.split(' ').filterTo(HashSet()) { it in VERSION_WORDS }

        class Fit(
            val item: SongItem,
            val title: TitleMatch,
            val off: Duration,
            val credited: Boolean
        )

        val fitting = results.mapNotNull { item ->
            val song = item.song.song
            val match = song.name?.let { titleMatch(title, words, it) }
            val otherVersion = song.name
                ?.let {
                    folded(it).split(' ').filterTo(HashSet()) { word -> word in VERSION_WORDS }
                }
                ?.let { it != versions } == true
            val off = song.duration?.let { (it - duration).absoluteValue }
            val credited = item.song.artists.any { artist ->
                val name = artist.name?.let(::folded).orEmpty()
                name.isNotEmpty() &&
                        names.any { " $name ".contains(" $it ") || " $it ".contains(" $name ") }
            }
            val misses = listOfNotNull(
                "no length".takeIf { off == null },
                "${off?.inWholeSeconds} s off".takeIf { off != null && off > REPLACEMENT_LENGTH_SLACK },
                "another title".takeIf { match == null },
                "another version".takeIf { otherVersion },
                "other artists, another title".takeIf {
                    match != null && !credited && match > TitleMatch.FOLDED
                },
            )
            Logger.d(tag = TAG) {
                if (misses.isEmpty()) {
                    "${song.id} fits: title ${match!!.text}, ${off!!.inWholeSeconds} s off, " +
                            if (credited) "same artist" else "other artists"
                } else {
                    "${song.id} doesn't fit: ${misses.joinToString()}"
                }
            }
            if (misses.isEmpty()) Fit(item, match!!, off!!, credited) else null
        }
        // Sorting keeps the order of equal ones, YouTube's
        val found = fitting
            .sortedWith(compareBy({ !it.credited }, { it.title }, { it.off }))
            .firstOrNull()
            ?.item
        Logger.i(tag = TAG) {
            if (found == null) {
                "No song of ${results.size} results matches a ${duration.inWholeSeconds} s track"
            } else {
                "${found.song.song.id} matches a ${duration.inWholeSeconds} s track, " +
                        "of ${fitting.size} that fit"
            }
        }
        return found
    }

    /**
     * How [name] is to [title], whose [folded] form is [words]; `null` when they share fewer than
     * half of the shorter one's words.
     */
    private fun titleMatch(title: String, words: String, name: String): TitleMatch? {
        val folded = folded(name)
        if (name == title) return TitleMatch.AS_WRITTEN
        if (folded == words) return TitleMatch.FOLDED
        if (" $folded ".contains(" $words ")) return TitleMatch.HOLDING
        val mine = words.split(' ').toSet()
        val theirs = folded.split(' ').filter { it.isNotEmpty() }.toSet()
        if (theirs.isEmpty()) return null
        val shared = mine.count { it in theirs }
        // At least half, rounded up, of the shorter title's words
        val needed = (minOf(mine.size, theirs.size) + 1) / 2
        return if (shared >= needed) TitleMatch.SHARING else null
    }

    /** [title] as [foldedWords]. */
    private fun folded(title: String): String = foldedWords(title)

    /** @return `null` when YouTube doesn't know the album */
    suspend fun albumPage(albumId: String): YouTubeAlbumPage? =
        parser.albumPage(albumId, innerTube.browse(albumId))

    /** @return `null` when YouTube doesn't know the artist */
    suspend fun artistPage(artistId: String): YouTubeArtistPage? =
        parser.artistPage(artistId, innerTube.browse(artistId))

    /**
     * Likes or dislikes the song, or takes its like or dislike away. A dislike replaces a like.
     *
     * @throws YouTubeException when YouTube can't be reached or refuses, with
     * [YouTubeException.signedOut] when nobody is signed in or the session ended
     */
    suspend fun rate(songId: String, status: LikeStatus) {
        innerTube.rate(songId, status)
    }

    /**
     * Saves the album to the user's library, or takes it out.
     *
     * @param playlistId the album's [YouTubeAlbumPage.playlistId]
     * @throws YouTubeException when YouTube can't be reached or refuses, with
     * [YouTubeException.signedOut] when nobody is signed in or the session ended
     */
    suspend fun saveAlbum(playlistId: String, saved: Boolean) {
        innerTube.likePlaylist(playlistId, saved)
    }

    /**
     * Saves the playlist to the user's library ("Add to library"), or takes it out.
     *
     * @param playlistId the playlist's id, without `VL`
     * @throws YouTubeException when YouTube can't be reached or refuses, with
     * [YouTubeException.signedOut] when nobody is signed in or the session ended
     */
    suspend fun savePlaylist(playlistId: String, saved: Boolean) {
        innerTube.likePlaylist(playlistId, saved)
    }

    /**
     * Subscribes the user to an artist, or unsubscribes them.
     *
     * @param channelId the artist's [YouTubeArtistPage.channelId]
     * @throws YouTubeException when YouTube can't be reached or refuses, with
     * [YouTubeException.signedOut] when nobody is signed in or the session ended
     */
    suspend fun subscribe(channelId: String, subscribed: Boolean) {
        innerTube.subscribe(channelId, subscribed, SUBSCRIBE_PARAMS)
    }

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
     * The next places of a playlist, with what names each for [removeFromPlaylist] and
     * [moveInPlaylist].
     *
     * @param continuation of [YouTubePlaylistPage] or of the previous call
     */
    suspend fun playlistEntries(continuation: String): YouTubePage<YouTubePlaylistEntry> =
        parser.playlistEntries(innerTube.continuation(Endpoint.BROWSE, continuation))

    /**
     * Takes places out of the playlist [playlistId] of the signed in user, all in one request.
     *
     * @param places for each the `setVideoId` naming the place and the video id of its song
     * @throws YouTubeException when YouTube can't be reached or refuses, like for a playlist that
     * isn't the user's, with [YouTubeException.signedOut] when nobody is signed in or the session
     * ended
     */
    suspend fun removeFromPlaylist(playlistId: String, places: List<Pair<String, String>>) {
        if (places.isEmpty()) return
        innerTube.editPlaylist(playlistId, places.map { (setVideoId, videoId) ->
            buildJsonObject {
                put("action", "ACTION_REMOVE_VIDEO")
                put("setVideoId", setVideoId)
                put("removedVideoId", videoId)
            }
        })
    }

    /**
     * Changes the name, description and who can see the playlist [playlistId] of the signed in
     * user, the ones that aren't `null`, in one request.
     *
     * @throws YouTubeException see [removeFromPlaylist]
     */
    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        description: String? = null,
        privacy: PlaylistPrivacy? = null,
    ) {
        val actions = buildList {
            if (name != null) add(buildJsonObject {
                put("action", "ACTION_SET_PLAYLIST_NAME")
                put("playlistName", name)
            })
            if (description != null) add(buildJsonObject {
                put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                put("playlistDescription", description)
            })
            if (privacy != null) add(buildJsonObject {
                put("action", "ACTION_SET_PLAYLIST_PRIVACY")
                put("playlistPrivacy", privacy.name)
            })
        }
        if (actions.isNotEmpty()) innerTube.editPlaylist(playlistId, actions)
    }

    /**
     * Moves the place [setVideoId] of the playlist [playlistId] of the signed in user to right
     * before the place [before], to the end when it is `null`.
     *
     * @throws YouTubeException see [removeFromPlaylist]
     */
    suspend fun moveInPlaylist(playlistId: String, setVideoId: String, before: String?) {
        innerTube.editPlaylist(playlistId, listOf(buildJsonObject {
            put("action", "ACTION_MOVE_VIDEO_BEFORE")
            put("setVideoId", setVideoId)
            put("movedSetVideoIdSuccessor", before)
        }))
    }

    /**
     * The playlists of the signed in user the song [songId] can be added to, the ones they made.
     *
     * @return `null` while nobody is signed in
     * @throws YouTubeException see [removeFromPlaylist]
     */
    suspend fun addablePlaylists(songId: String): List<UserPlaylist>? {
        if (!isSignedIn()) return null

        return parser.addToPlaylistOptions(innerTube.addToPlaylistOptions(songId))
    }

    /**
     * Adds the song [songId] to the end of the playlist [playlistId] of the signed in user, unless
     * it has it already.
     *
     * @return `false` when the playlist had the song
     * @throws YouTubeException see [removeFromPlaylist]
     */
    suspend fun addToPlaylist(playlistId: String, songId: String): Boolean =
        innerTube.addToPlaylist(playlistId, songId)

    /**
     * Makes a playlist for the signed in user.
     *
     * @param songIds ids of the songs it starts with
     * @return the id of the playlist, or `null` while nobody is signed in
     * @throws YouTubeException see [removeFromPlaylist]
     */
    suspend fun createPlaylist(
        title: String,
        description: String,
        privacy: PlaylistPrivacy,
        songIds: List<String> = emptyList(),
    ): String? {
        if (!isSignedIn()) return null

        return innerTube.createPlaylist(title, description, privacy.name, songIds)
    }

    /**
     * Deletes the playlist [playlistId] of the signed in user.
     *
     * @throws YouTubeException see [removeFromPlaylist]
     */
    suspend fun deletePlaylist(playlistId: String) {
        innerTube.deletePlaylist(playlistId)
    }

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
            if (continuation == null)
                innerTube.browse(endpoint.browseId, endpoint.params)
            else
                innerTube.continuation(Endpoint.BROWSE, continuation)
        )

    /**
     * The rows of the home page, the same for everyone in a country as nobody is signed in.
     *
     * @param continuation the token of the previous rows, `null` for the first ones
     */
    suspend fun home(continuation: String? = null): YouTubePage<YouTubeSection> =
        parser.homePage(
            if (continuation == null)
                innerTube.browse(HOME)
            else
                innerTube.continuation(Endpoint.BROWSE, continuation)
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
            if (continuation == null)
                innerTube.next(songId, playlistId)
            else
                innerTube.continuation(Endpoint.NEXT, continuation, playlistId)
        )
    }

    /**
     * The songs [start] plays, about 25 at a time, in the order YouTube gives: shuffled for a
     * playlist's "Shuffle play".
     *
     * @param continuation the token of the previous songs, `null` for the first ones. The same
     * [start] must be given with it.
     */
    suspend fun queue(
        start: YouTubeQueueStart,
        continuation: String? = null
    ): YouTubePage<SongItem> =
        parser.queue(
            if (continuation == null)
                innerTube.next(start.videoId, start.playlistId, start.params)
            else
                innerTube.continuation(Endpoint.NEXT, continuation, start.playlistId)
        )

    /** Endless songs like [songId], starting with it. */
    suspend fun radio(songId: String, continuation: String? = null): YouTubePage<SongItem> =
        upNext(songId, RADIO_PREFIX + songId, continuation)

    /** @return `null` when YouTube has no lyrics for the song */
    suspend fun lyrics(songId: String): YouTubeLyrics? {
        val browseId =
            parser.tabBrowseId(innerTube.next(songId, null), LYRICS_PREFIX) ?: return null
        return parser.lyrics(innerTube.browse(browseId))
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
                    if (more == null)
                        section.items
                    else
                        browse(more).items.map { it.creditedTo(name) }
                }
            }.awaitAll()
        }.flatten().filterIsInstance<AlbumItem>().map { it.album }.distinctBy { it.id }
    }

    /**
     * The audio to play of [songId], the video id of a song or any other video. The visionOS app
     * is asked first, the website with a PO token after it. It is asked without the user's
     * session, so what needs one, like videos with an age limit, can't be played, unless
     * [Preferences.YOUTUBE_PLAYBACK_LOGIN] says otherwise. [StreamQuality.ORIGINAL] is always asked
     * in it while someone is signed in, for Premium's audio.
     *
     * @param refused the [YouTubeStream.client]s whose streams YouTube refused for this song,
     * like one that stopped after its first part. Others are asked instead.
     * @param quality how good the audio should be, YouTube gives the nearest it has
     * @return `null` when the song can't be played
     */
    suspend fun stream(
        songId: String,
        refused: Set<String> = emptySet(),
        quality: StreamQuality = StreamQuality.HIGH,
    ): YouTubeStream? = innerTube.stream(songId, refused, quality)

    /** The address of [stream]. */
    override suspend fun playbackUri(songId: String): Uri? = stream(songId)?.url?.toUri()

    /**
     * The client going through YouTube's proxy now, for the player to read the addresses of
     * [stream] with: YouTube refuses a stream read from another network than the one that asked
     * for it. Ask again for each request, it changes with the proxy.
     */
    suspend fun streamClient(): Call.Factory = clients().currentOkHttp()

    /**
     * Like [streamClient], for what reads pictures and other addresses of YouTube with Ktor,
     * see [isYouTubeHost].
     */
    suspend fun httpClient(): HttpClient = clients().current()

    private fun clients(): YouTubeClients =
        checkNotNull(clients) { "Made with a client of its own, it has no proxy" }

    /**
     * @param artworkId the address of a picture as YouTube gave it, like [Song.thumbnail]
     */
    override fun artworkUri(artworkId: String, size: Int?): Uri? =
        artworkId.takeIf { it.isNotBlank() }?.let { resizeArtwork(it, size).toUri() }

    /**
     * A frame of the song's video, every song has one. A song of an album shows its cover in
     * the frame, a music video a moment of the video. Larger than [MEDIUM_FRAME], the wide frame
     * comes first: the others have black bars above and below, but not every video has it.
     *
     * Larger than [MEDIUM_FRAME], a cover the song came with comes before the frames: it is
     * square and comes at any size, the frames are at most 720 high.
     */
    override fun songArtworkUris(
        songId: String,
        albumId: String?,
        size: Int?,
        artwork: Uri?,
    ): List<Uri> =
        listOfNotNull(
            artwork?.toString()
                ?.takeIf { (size == null || size > MEDIUM_FRAME) && isSizedArtwork(it) }
                ?.let { resizeArtwork(it, size).toUri() },
            "https://i.ytimg.com/vi/$songId/hq720.jpg".toUri()
                .takeIf { size == null || size > MEDIUM_FRAME },
            artworkUri("https://i.ytimg.com/vi/$songId/hqdefault.jpg", size),
        )

    private companion object {
        /**
         * What YouTube answers with for an id it doesn't know: 404, or 500 when the request
         * carries a visitor id (seen on a single unknown id, 2026-11-05).
         */
        const val NOT_FOUND = 404
        const val INTERNAL_ERROR = 500

        /** Most ids sent in one request by [lookUpSongs]. */
        const val LOOK_UP_BATCH = 1000

        fun innerTube(
            client: suspend () -> HttpClient,
            json: Json,
            context: Context?,
            cookies: suspend () -> String,
            pageId: suspend () -> String,
            region: () -> String = ::youTubeRegion,
        ) = InnerTube(
            client,
            json,
            Locale::getDefault,
            region,
            cookies,
            pageId = pageId,
            poTokens = context?.let { WebPoTokenProvider(it, client) },
            playbackLogin = { Preferences.YOUTUBE_PLAYBACK_LOGIN.value },
        )

        suspend fun profileCookies(): String =
            if (Preferences.YOUTUBE_ACCOUNT_ENABLED.value) Secrets.YOUTUBE_COOKIES.current() else ""

        suspend fun profilePageId(): String = Secrets.YOUTUBE_PAGE_ID.current()

        const val HOME = "FEmusic_home"

        /**
         * What YouTube Music's subscribe button sends, a page asked outside the session doesn't
         * give it (taken from Metrolist).
         */
        const val SUBSCRIBE_PARAMS = "EgIIAhgA"

        /** Playlist id of the radio of a song is this followed by the song's id. */
        const val RADIO_PREFIX = "RDAMVM"

        /** Requests a search of one kind may send for [Provider.search]. */
        const val SEARCH_PAGES = 3

        /**
         * How much the length of the song published in place of another may differ from its,
         * see [replacementOf]. YouTube's lengths are whole seconds and differ by one between
         * uploads of the same recording.
         */
        val REPLACEMENT_LENGTH_SLACK = 2.seconds

        const val TAG = "YouTubeProvider"

        /**
         * Folded words that name a version of a song, see [songMatching]. "Mix" and "original"
         * aren't: other services write "Original Mix" where YouTube writes nothing; nor is
         * "remaster", a remaster is the song the user wants.
         */
        val VERSION_WORDS = setOf(
            "instrumental", "remix", "live", "acoustic", "karaoke", "edit", "vip", "extended",
            "cover", "sped", "slowed", "nightcore", "demo", "version",
        )



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

/**
 * The ids of the albums saved to, or artists subscribed to in, the user's library on YouTube.
 *
 * @property ids the ids listed
 * @property complete whether that is the whole library, `false` when it is longer than what was
 * asked for, an id not listed may be in it then
 */
class LibraryIds(val ids: Set<String>, val complete: Boolean)

/** A page of the library, see [YouTubeProvider.libraryAlbumIds]. */
private class LibraryItems(val items: List<YouTubeItem>, val complete: Boolean)

/** What isn't part of a word of a title, see [foldedWords]. */
private val NOT_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")

/**
 * [text] as words to compare, the way [YouTubeProvider] compares titles and names when it matches
 * songs: its compatibility forms (full-width letters become plain ones), in lower case, with
 * anything but letters and digits between words, like "-version 2022-" and "(Version 2022)" both
 * becoming "version 2022"; "ft" and "featuring" are "feat".
 */
fun foldedWords(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .split(NOT_WORD)
        .filter { it.isNotEmpty() }
        .joinToString(" ") { if (it in FEATURING) "feat" else it }

/** How titles name a featured artist, see [foldedWords]. */
private val FEATURING = setOf("ft", "featuring")
