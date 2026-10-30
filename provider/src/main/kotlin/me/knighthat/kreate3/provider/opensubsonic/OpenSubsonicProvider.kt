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

package me.knighthat.kreate3.provider.opensubsonic

import android.net.Uri
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.SearchResults
import okhttp3.Call
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Clock
import kotlin.time.Instant

private const val API_VERSION = "1.16.1"
private const val CLIENT_NAME = "kreate"

/**
 * A server that speaks the OpenSubsonic API, like Navidrome.
 *
 * It is set up by [OpenSubsonicSettings]. While they are off nothing is sent to the server and no
 * address is built, whatever address and credentials are saved. The library can be listed,
 * browsed into and searched.
 */
class OpenSubsonicProvider internal constructor(
    private val client: suspend () -> HttpClient,
    private val json: Json,
    private val settings: OpenSubsonicSettings,
    private val clock: Clock = Clock.System,
    private val okHttp: suspend () -> OkHttpClient = { error("No client to stream with") },
) : Provider {

    /**
     * @param base the app's client, the provider derives its own from it, see [OpenSubsonicClients]
     */
    constructor(base: OkHttpClient, json: Json) : this(
        OpenSubsonicClients(base, ProfileOpenSubsonicSettings),
        json,
        ProfileOpenSubsonicSettings,
    )

    private constructor(
        clients: OpenSubsonicClients,
        json: Json,
        settings: OpenSubsonicSettings,
    ) : this(clients::current, json, settings, okHttp = clients::currentOkHttp)

    override val origin = Origin.OPEN_SUBSONIC

    /** The last [shareRole] read, with the server and sign in it is for. */
    @Volatile
    private var knownShareRole: Pair<String, Boolean>? = null

    /** The OpenSubsonic extensions the server last said it has, with the server they are of. */
    @Volatile
    private var knownExtensions: Pair<String, Set<String>>? = null

    /**
     * Whether a server answers at the address, and what it says about itself. No credentials
     * are sent, a server that refuses the missing sign in still answers like a Subsonic server and
     * counts as available. Use [checkCredentials] to know if the sign in works.
     *
     * Returns [ServerCheck.Success] when the server answered, [ServerCheck.Disabled] and
     * [ServerCheck.NotConfigured] without any request, and [ServerCheck.Unreachable] or
     * [ServerCheck.NotSubsonic] when it did not.
     */
    suspend fun ping(): ServerCheck {
        if (!settings.enabled) return ServerCheck.Disabled
        if (settings.url.isBlank()) return ServerCheck.NotConfigured

        return when (val answer = request("ping", emptyList())) {
            is Answer.Failure -> answer.check
            // A refused sign in still proves the server is there
            is Answer.Response -> ServerCheck.Success(answer.info)
        }
    }

    /**
     * Whether the saved username and password, or API key, are accepted by the server.
     *
     * Returns [ServerCheck.Success] when they are, [ServerCheck.Rejected] when the server refuses
     * them, [ServerCheck.Disabled] and [ServerCheck.NotConfigured] without any request, and
     * [ServerCheck.Unreachable] or [ServerCheck.NotSubsonic] when the server can't be used.
     */
    suspend fun checkCredentials(): ServerCheck {
        if (!settings.enabled) return ServerCheck.Disabled
        if (settings.url.isBlank()) return ServerCheck.NotConfigured
        val credentials = credentials() ?: return ServerCheck.NotConfigured

        return when (val answer = request("ping", credentials)) {
            is Answer.Failure -> answer.check
            is Answer.Response ->
                if (answer.ok) ServerCheck.Success(answer.info)
                else ServerCheck.Rejected(answer.errorCode, answer.errorMessage)
        }
    }

    /**
     * Address of the server, without `/rest` or a trailing `/`, `null` while the server is off or
     * has no address. Items synced from one address are replaced when it changes.
     */
    val serverUrl: String?
        get() = settings.url.trim().trimEnd('/').takeIf { settings.enabled && it.isNotEmpty() }

    /**
     * Every album on the server with its star, in pages of [PAGE_SIZE].
     *
     * @param starredOnly only the albums the user starred
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun libraryAlbums(starredOnly: Boolean = false): List<RemoteItem<Album>>? {
        serverUrl ?: return null

        val albums = mutableListOf<RemoteItem<Album>>()
        do {
            val page = call(
                "getAlbumList2",
                "type" to if (starredOnly) "starred" else "alphabeticalByName",
                "size" to PAGE_SIZE.toString(),
                "offset" to albums.size.toString(),
            )["albumList2"]?.jsonObject?.array("album").orEmpty()
            page.mapTo(albums) { it.jsonObject.toAlbum(::coverUri) }
        } while (page.size == PAGE_SIZE)

        return albums
    }

    /**
     * Every artist on the server with its star.
     *
     * @param starredOnly only the artists the user starred
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun libraryArtists(starredOnly: Boolean = false): List<RemoteItem<Artist>>? {
        serverUrl ?: return null

        return call("getArtists")["artists"]?.jsonObject?.array("index").orEmpty()
            .flatMap { it.jsonObject.array("artist").orEmpty() }
            .map { it.jsonObject.toArtist(::coverUri) }
            .filter { !starredOnly || it.starredAt != null }
    }

    /**
     * The playlists the user can see on the server, theirs and the public ones.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun libraryPlaylists(): List<UserPlaylist>? {
        serverUrl ?: return null

        return call("getPlaylists")["playlists"]?.jsonObject?.array("playlist").orEmpty()
            .map { it.jsonObject.toPlaylist(::coverUri) }
    }

    /**
     * Searches the server's library for [query]: up to [songs] songs, [albums] albums and
     * [artists] artists, each kind after its first [offset]. A count of `0` leaves that kind out.
     * The API doesn't search playlists.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun searchLibrary(
        query: String,
        songs: Int,
        albums: Int,
        artists: Int,
        offset: Int = 0,
    ): OpenSubsonicSearch? {
        serverUrl ?: return null

        val found = call(
            "search3",
            "query" to query,
            "songCount" to songs.toString(),
            "songOffset" to offset.toString(),
            "albumCount" to albums.toString(),
            "albumOffset" to offset.toString(),
            "artistCount" to artists.toString(),
            "artistOffset" to offset.toString(),
        )["searchResult3"]?.jsonObject ?: throw ServerException(ServerCheck.NotSubsonic)

        // A server may still list a kind it was asked none of
        return OpenSubsonicSearch(
            songs = found.array("song").orEmpty().take(songs)
                .map { it.jsonObject.toSong(::coverUri) },
            albums = found.array("album").orEmpty().take(albums)
                .map { it.jsonObject.toAlbum(::coverUri) },
            artists = found.array("artist").orEmpty().take(artists)
                .map { it.jsonObject.toArtist(::coverUri) },
        )
    }

    /**
     * The song with its album and artists, as the server has it now.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun songDetails(songId: String): RemoteSong? {
        serverUrl ?: return null

        return call("getSong", "id" to songId)["song"]?.jsonObject?.toSong(::coverUri)
            ?: throw ServerException(ServerCheck.NotSubsonic)
    }

    /**
     * The format of the song's file on the server, with its ReplayGain as the gain.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun format(songId: String): SongFormat? {
        serverUrl ?: return null

        return call("getSong", "id" to songId)["song"]?.jsonObject?.toSongFormat()
            ?: throw ServerException(ServerCheck.NotSubsonic)
    }

    /**
     * An album of the server with its songs, in the album's order.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun albumWithSongs(albumId: String): Pair<RemoteItem<Album>, List<RemoteSong>>? {
        serverUrl ?: return null

        val album = call("getAlbum", "id" to albumId)["album"]?.jsonObject
            ?: throw ServerException(ServerCheck.NotSubsonic)
        return album.toAlbum(::coverUri) to album.array("song").orEmpty()
            .map { it.jsonObject.toSong(::coverUri) }
    }

    /**
     * An artist of the server with their albums and the songs on them. The API has no list of
     * an artist's songs, so every album is asked for, a few at a time.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses
     */
    suspend fun artistWithSongs(artistId: String): ArtistWithSongs? {
        val (artist, listed) = artistWithAlbums(artistId) ?: return null

        val gate = Semaphore(PARALLEL_REQUESTS)
        val albums = coroutineScope {
            listed.map { async { gate.withPermit { albumWithSongs(it.item.id) } } }.awaitAll()
                .filterNotNull()
        }
        return ArtistWithSongs(
            artist = artist,
            albums = albums.map { it.first },
            songs = albums.flatMap { it.second },
        )
    }

    /**
     * An artist of the server with their albums, without the songs on them.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun artistWithAlbums(artistId: String): Pair<RemoteItem<Artist>, List<RemoteItem<Album>>>? {
        serverUrl ?: return null

        val artist = call("getArtist", "id" to artistId)["artist"]?.jsonObject
            ?: throw ServerException(ServerCheck.NotSubsonic)
        return artist.toArtist(::coverUri) to artist.array("album").orEmpty()
            .map { it.jsonObject.toAlbum(::coverUri) }
    }

    /**
     * Up to [count] songs of the artist [artistId], the most popular first as the server ranks
     * them with `getTopSongs`, which servers build from outside metadata, like Last.fm or
     * ListenBrainz for Navidrome. When it ranks fewer, or none, the rest come from a search of
     * [name], in the search's order. Songs of other artists either finds are left out.
     *
     * @param name the artist's name, `getTopSongs` asks by name
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the search
     */
    suspend fun topSongs(artistId: String, name: String, count: Int): List<RemoteSong>? {
        serverUrl ?: return null

        fun List<RemoteSong>.theirs() = filter { song -> song.artists.any { it.id == artistId } }

        // A server without the method refuses it, one without outside metadata ranks none
        val top = try {
            val found = call("getTopSongs", "artist" to name, "count" to count.toString())
            (found["topSongs"] as? JsonObject)?.array("song").orEmpty()
                .map { it.jsonObject.toSong(::coverUri) }
                .theirs()
        } catch (e: ServerException) {
            if (e.check !is ServerCheck.Rejected) throw e
            emptyList()
        }
        if (top.size >= count) return top.take(count)

        val found = searchLibrary(name, songs = TOP_SONGS_SEARCH, albums = 0, artists = 0)
            ?.songs.orEmpty()
            .theirs()
        return (top + found).distinctBy { it.song.id }.take(count)
    }

    /**
     * Notes about the album as plain text, like its description on Last.fm.
     *
     * @return `null` while the server is off or not set up, or when it has none
     * @throws ServerException when the server can't be used or refuses, like one that has no
     * album info
     */
    suspend fun albumNotes(albumId: String): String? {
        serverUrl ?: return null

        val info = call("getAlbumInfo2", "id" to albumId)["albumInfo"] as? JsonObject
        return (info?.get("notes") as? JsonPrimitive)?.contentOrNull?.let(::htmlToText)
    }

    /**
     * The biography of the artist as plain text, like the one on Last.fm.
     *
     * @return `null` while the server is off or not set up, or when it has none
     * @throws ServerException when the server can't be used or refuses, like one that has no
     * artist info
     */
    suspend fun artistBiography(artistId: String): String? {
        serverUrl ?: return null

        // Similar artists come with the biography, none are needed
        val info = call("getArtistInfo2", "id" to artistId, "count" to "0")["artistInfo2"]
                as? JsonObject
        return (info?.get("biography") as? JsonPrimitive)?.contentOrNull?.let(::htmlToText)
    }

    /**
     * A playlist of the server with its songs, in its order.
     *
     * @param playlistId id of the playlist on the server
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses
     */
    suspend fun playlistWithSongs(playlistId: String): Pair<UserPlaylist, List<RemoteSong>>? {
        serverUrl ?: return null

        val playlist = call("getPlaylist", "id" to playlistId)["playlist"]?.jsonObject
            ?: throw ServerException(ServerCheck.NotSubsonic)
        return playlist.toPlaylist(::coverUri) to playlist.array("entry").orEmpty()
            .map { it.jsonObject.toSong(::coverUri) }
    }

    /**
     * Every song the user starred on the server, each with when it was starred.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun starredSongs(): List<RemoteSong>? {
        serverUrl ?: return null

        val starred = call("getStarred2")["starred2"]?.jsonObject
            ?: throw ServerException(ServerCheck.NotSubsonic)
        return starred.array("song").orEmpty().map { it.jsonObject.toSong(::coverUri) }
    }

    /**
     * Stars [songId] on the server, or takes its star away. The API has no dislike, a star is all
     * the server keeps of what the user thinks of a song.
     *
     * @return `false` while the server is off or not set up, nothing is sent then
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun setStarred(songId: String, starred: Boolean): Boolean =
        star("id" to songId, starred)

    /**
     * Stars the album [albumId] on the server, or takes its star away, see [setStarred].
     *
     * @return `false` while the server is off or not set up, nothing is sent then
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun setAlbumStarred(albumId: String, starred: Boolean): Boolean =
        star("albumId" to albumId, starred)

    /**
     * Stars the artist [artistId] on the server, or takes their star away, see [setStarred].
     *
     * @return `false` while the server is off or not set up, nothing is sent then
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun setArtistStarred(artistId: String, starred: Boolean): Boolean =
        star("artistId" to artistId, starred)

    /** Sends `star` or `unstar` for [item], the parameter naming what it is with its id. */
    private suspend fun star(item: Pair<String, String>, starred: Boolean): Boolean {
        serverUrl ?: return false

        call(if (starred) "star" else "unstar", item)
        return true
    }

    /**
     * Songs the server finds similar to [songId], most similar first. Servers build this from
     * outside metadata, like Last.fm for Navidrome, so it may be empty.
     *
     * @param count how many songs to ask for
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like for an id it doesn't have
     */
    suspend fun similarSongs(songId: String, count: Int): List<RemoteSong>? {
        serverUrl ?: return null

        // A server with nothing similar may leave the object out
        val response = call("getSimilarSongs2", "id" to songId, "count" to count.toString())
        val similar = response["similarSongs2"]?.jsonObject ?: return emptyList()
        return similar.array("song").orEmpty().map { it.jsonObject.toSong(::coverUri) }
    }

    /**
     * A public link to [item]. Anyone with it can open and play the item without an account,
     * until it is deleted or expires on the server.
     *
     * A share of the user's that holds exactly the item and hasn't expired is used again, the
     * server makes a new share at every request otherwise. Only when there is none a share is
     * made. Navidrome also shares an artist, as all of their albums, other servers may not.
     *
     * Check [shareRole] first, a user without it can't make shares.
     *
     * @param description shown on the shared page of a new share, like the item's name
     * @return the link, `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like for a kind of item
     * it doesn't share
     */
    suspend fun shareLink(item: Shareable, description: String?): String? {
        serverUrl ?: return null

        existingShare(entriesOf(item))?.let { return it }

        val params = listOfNotNull("id" to item.id, description?.let { "description" to it })
        return call("createShare", *params.toTypedArray())["shares"]?.jsonObject
            ?.array("share")?.firstOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            ?: throw ServerException(ServerCheck.NotSubsonic)
    }

    /**
     * Ids of what a share of [item] holds: the song or the album itself, every album of the
     * artist, or every song of the playlist.
     */
    private suspend fun entriesOf(item: Shareable): Set<String> = when (item) {
        is Shareable.Song, is Shareable.Album -> setOf(item.id)
        is Shareable.Artist -> call("getArtist", "id" to item.id)["artist"]?.jsonObject
            ?.array("album").orEmpty().ids()

        is Shareable.Playlist -> call("getPlaylist", "id" to item.id)["playlist"]?.jsonObject
            ?.array("entry").orEmpty().ids()
    }

    /**
     * Link of a share of the user's that holds exactly [entries] and hasn't expired, `null` when
     * there is none.
     */
    private suspend fun existingShare(entries: Set<String>): String? {
        if (entries.isEmpty()) return null
        val now = clock.now()

        return call("getShares")["shares"]?.jsonObject?.array("share").orEmpty()
            .map { it.jsonObject }
            .firstOrNull { share ->
                val expires = share["expires"]?.jsonPrimitive?.contentOrNull
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                (expires == null || expires > now) && share.array("entry").orEmpty()
                    .ids() == entries
            }
            ?.get("url")?.jsonPrimitive?.contentOrNull
    }

    private fun List<JsonElement>.ids(): Set<String> =
        mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.toSet()

    /**
     * Whether the signed in user may make shares, the `shareRole` of `getUser`. With an API key the
     * username comes from `tokenInfo`. The answer is kept for the server and sign in it was read
     * with, a change of the role on the server shows after the app restarts.
     *
     * @return `null` when it can't be read, like while the server is off or from a server that
     * doesn't tell
     */
    suspend fun shareRole(): Boolean? {
        val server = serverUrl ?: return null
        val key = "$server|${settings.auth}|${settings.username()}"
        knownShareRole?.takeIf { it.first == key }?.let { return it.second }

        return try {
            val username = when (settings.auth) {
                OpenSubsonicAuth.PASSWORD -> settings.username()
                OpenSubsonicAuth.API_KEY -> call("tokenInfo")["tokenInfo"]?.jsonObject
                    ?.get("username")?.jsonPrimitive?.contentOrNull
            }
            username?.let {
                call("getUser", "username" to it)["user"]?.jsonObject
                    ?.get("shareRole")?.jsonPrimitive?.booleanOrNull
            }?.also { knownShareRole = key to it }
        } catch (_: ServerException) {
            null
        }
    }

    /**
     * The picture behind a cover address, signed in. Only an address of the server set now is
     * loaded, see [isCoverUri].
     *
     * @param size edge in pixels the picture is needed at, `null` for the original
     * @return `null` when the server is off, the address is of another server or there is no
     * such picture
     * @throws ServerException when the server can't be used
     */
    suspend fun cover(uri: Uri, size: Int?): ByteArray? {
        if (!isCoverOfServer(uri)) return null
        val base = serverUrl ?: return null
        val id = uri.getQueryParameter("id") ?: return null
        val credentials = credentials() ?: return null

        val response = try {
            client().submitForm(
                url = "$base/rest/getCoverArt",
                formParameters = parameters {
                    (credentials + common().filter { it.first != "f" }).forEach { (k, v) ->
                        append(
                            k,
                            v
                        )
                    }
                    append("id", id)
                    size?.let { append("size", it.toString()) }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ServerException(failureOf(e))
        }
        // An error comes back as JSON or XML, a picture never does
        val type = response.contentType()
        return if (type?.contentType == "image") response.bodyAsBytes() else null
    }

    /**
     * Whether [uri] is a cover address as stored on the items of an OpenSubsonic server:
     * `getCoverArt` with the id and without the sign in. It matches the addresses of any server,
     * so a cover is never sent to the server without going through [cover], which refuses it
     * while the server is off.
     */
    fun isCoverUri(uri: Uri): Boolean =
        (uri.scheme == "https" || uri.scheme == "http") &&
                uri.path.orEmpty().endsWith("/rest/getCoverArt") &&
                uri.getQueryParameter("id") != null

    /**
     * Whether [uri] is a cover address of the server set now, `false` while the server is off.
     * A cover saved from the server is only shown while this holds, like one loaded from it.
     */
    fun isCoverOfServer(uri: Uri): Boolean {
        val base = serverUrl ?: return false
        return isCoverUri(uri) && uri.toString().substringBefore('?') == "$base/rest/getCoverArt"
    }

    /**
     * Address of a cover of this server without the sign in, which must not be stored. [cover]
     * adds it when the picture is loaded.
     */
    private fun coverUri(coverArtId: String): Uri? =
        serverUrl?.let {
            "$it/rest/getCoverArt".toUri().buildUpon().appendQueryParameter("id", coverArtId)
                .build()
        }

    /**
     * Sends [method] signed in.
     *
     * @return the `subsonic-response` of the answer
     * @throws ServerException when the request fails or the server refuses it
     */
    /**
     * @param body sent as JSON, with the parameters in the address, for methods that take one
     */
    private suspend fun call(
        method: String,
        vararg params: Pair<String, String>,
        body: JsonObject? = null,
    ): JsonObject {
        val credentials = credentials() ?: throw ServerException(ServerCheck.NotConfigured)

        return when (val answer = request(method, credentials, params.toList(), body)) {
            is Answer.Failure -> throw ServerException(answer.check)
            is Answer.Response ->
                if (answer.ok) answer.envelope
                else throw ServerException(
                    ServerCheck.Rejected(
                        answer.errorCode,
                        answer.errorMessage
                    )
                )
        }
    }

    /**
     * Trusts the new certificate of [change] from now on, in place of the old one.
     */
    fun trust(change: ServerCheck.CertificateChanged) =
        settings.pin(CertificatePin(change.host, change.fingerprint))

    /**
     * Forgets the trusted self-signed certificate, the next one the server presents is trusted.
     */
    fun forgetCertificate() = settings.pin(null)

    override suspend fun search(query: String, limit: Int, offset: Int): SearchResults {
        if (query.isBlank() || limit <= 0) return SearchResults.EMPTY
        val found = searchLibrary(query, limit, limit, limit, offset) ?: return SearchResults.EMPTY
        return SearchResults(
            songs = found.songs.map { it.song },
            albums = found.albums.map { it.item },
            artists = found.artists.map { it.item },
        )
    }

    override suspend fun song(songId: String): Song? = songDetails(songId)?.song

    override suspend fun album(albumId: String): Album? = albumWithSongs(albumId)?.first?.item

    override suspend fun artist(artistId: String): Artist? = artistWithSongs(artistId)?.artist?.item

    override suspend fun songsOf(albumId: String): List<Song> =
        albumWithSongs(albumId)?.second?.map { it.song }.orEmpty()

    override suspend fun albumsOf(artistId: String): List<Album> =
        artistWithSongs(artistId)?.albums?.map { it.item }.orEmpty()

    /**
     * The address of the song signed in, read it with [streamClient]. It holds the sign in, never
     * store or log it.
     */
    override suspend fun playbackUri(songId: String): Uri? = endpoint("stream", "id" to songId)

    /**
     * What to stream of [songId] in [quality]. [StreamQuality.ORIGINAL] is the file as it is, the
     * others are a highest bitrate: songs above it are converted by the server, the others are
     * sent as they are.
     *
     * A server with the `transcoding` extension is asked what it sends first, which gives the
     * format it converts to and the formats the player reads without converting. Others get the
     * highest bitrate and pick the format, which the answer to the stream tells, see
     * [ServerStream.answeredFormat].
     *
     * A converted song has an estimated length, so the player can seek in it: until the server
     * has it all, it answers a part with the song from the start, and the song may end a little
     * before that length.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server was asked what it sends and can't be used
     */
    suspend fun stream(songId: String, quality: StreamQuality): ServerStream? {
        val bitrate = quality.maxBitRate
            ?: return endpoint("stream", "id" to songId, "format" to "raw")
                ?.let { ServerStream(it, null, null) }
        serverUrl ?: return null

        if (TRANSCODING in extensions()) decidedStream(songId, bitrate)?.let { return it }
        return endpoint(
            "stream",
            "id" to songId,
            "maxBitRate" to bitrate.toString(),
            "estimateContentLength" to "true",
        )?.let { ServerStream(it, null, bitrate) }
    }

    /**
     * What the server decided to send of [songId] at most [bitrate] kbps, `null` when it didn't
     * decide.
     */
    private suspend fun decidedStream(songId: String, bitrate: Int): ServerStream? {
        val decision = call(
            "getTranscodeDecision",
            "mediaId" to songId,
            "mediaType" to "song",
            body = clientInfo(bitrate * 1000),
        )["transcodeDecision"]?.jsonObject ?: return null

        if (decision["canDirectPlay"]?.jsonPrimitive?.booleanOrNull == true)
            return endpoint("stream", "id" to songId, "format" to "raw")
                ?.let { ServerStream(it, null, null) }
        if (decision["canTranscode"]?.jsonPrimitive?.booleanOrNull != true) return null

        val params = decision["transcodeParams"]?.jsonPrimitive?.contentOrNull ?: return null
        val format = decision["transcodeStream"]?.jsonObject?.toTranscodedFormat(songId)
            ?: return null
        return endpoint(
            "getTranscodeStream",
            "mediaId" to songId,
            "mediaType" to "song",
            "transcodeParams" to params,
            "estimateContentLength" to "true",
        )?.let { ServerStream(it, format, null) }
    }

    /**
     * The OpenSubsonic extensions the server has, by name, asked once for each server. None
     * while it can't be asked, it is asked again next time.
     */
    private suspend fun extensions(): Set<String> {
        val server = serverUrl ?: return emptySet()
        knownExtensions?.takeIf { it.first == server }?.let { return it.second }

        return try {
            val names = call("getOpenSubsonicExtensions")["openSubsonicExtensions"]
                ?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.toSet()
                .orEmpty()
            names.also { knownExtensions = server to it }
        } catch (_: ServerException) {
            emptySet()
        }
    }

    /**
     * The client that trusts what the user chose to trust, like a self-signed certificate, for
     * the player to read [playbackUri] with. Use it for addresses of this server only, and ask
     * again for each request, it changes with what is trusted and with the proxy.
     */
    suspend fun streamClient(): Call.Factory = okHttp()

    /**
     * The cover address stored on items, which [cover] loads. The server scales it to the size
     * asked when it is loaded, so [size] is not part of it.
     */
    override fun artworkUri(artworkId: String, size: Int?): Uri? =
        coverUri(artworkId)

    /**
     * Navidrome takes the id of a song or an album as the id of a cover, other servers may only
     * take the ids they give as `coverArt`.
     */
    override fun songArtworkUris(
        songId: String,
        albumId: String?,
        size: Int?,
        artwork: Uri?,
    ): List<Uri> =
        listOfNotNull(coverUri(songId), albumId?.let(::coverUri))

    private sealed interface Answer {
        class Failure(val check: ServerCheck) : Answer
        class Response(
            val envelope: JsonObject,
            val ok: Boolean,
            val info: ServerInfo,
            val errorCode: Int,
            val errorMessage: String,
        ) : Answer
    }

    /**
     * The values that sign a request in, `null` when the chosen method has none saved. Only the
     * credentials of the chosen method are used. A password never leaves the device, only a hash
     * of it with a new salt does.
     */
    private suspend fun credentials(): List<Pair<String, String>>? = when (settings.auth) {
        OpenSubsonicAuth.PASSWORD -> {
            val username = settings.username()
            val password = settings.password()
            if (username.isEmpty() || password.isEmpty()) null
            else {
                val salt = ByteArray(8).also(SecureRandom()::nextBytes).toHex()
                val token = MessageDigest.getInstance("MD5")
                    .digest((password + salt).toByteArray()).toHex()
                listOf("u" to username, "t" to token, "s" to salt)
            }
        }

        OpenSubsonicAuth.API_KEY -> settings.apiKey().takeIf { it.isNotEmpty() }
            ?.let { listOf("apiKey" to it) }
    }

    /**
     * Sends [method] as a form in the body of a POST. The sign in stays out of the address, which
     * is what ends up in logs.
     */
    private suspend fun request(
        method: String,
        signIn: List<Pair<String, String>>,
        params: List<Pair<String, String>> = emptyList(),
        body: JsonObject? = null,
    ): Answer = withContext(Dispatchers.IO) {
        val url = "${settings.url.trimEnd('/')}/rest/$method"
        val values = signIn + common() + params
        // Decoding a large library's answer takes long, never on the caller's thread, which may
        // be the main one
        val answer = try {
            if (body == null)
                client().submitForm(
                    url = url,
                    formParameters = parameters {
                        values.forEach { (key, value) -> append(key, value) }
                    },
                ).bodyAsText()
            else
                client().post(url) {
                    values.forEach { (key, value) -> parameter(key, value) }
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext Answer.Failure(failureOf(e))
        }

        parse(answer).also {
            if (it is Answer.Response) OpenSubsonicServer.record(
                settings.url,
                it.info
            )
        }
    }

    /**
     * What went wrong, looking through causes and suppressed failures too: a host with several
     * addresses reports the first one that failed, with the others suppressed in it.
     */
    private fun failureOf(e: Exception): ServerCheck {
        val failures = mutableListOf<Throwable>()
        val pending = ArrayDeque<Throwable>(listOf(e))
        while (pending.isNotEmpty()) {
            val failure = pending.removeFirst()
            if (failures.any { it === failure }) continue
            failures += failure
            failure.cause?.let(pending::add)
            pending += failure.suppressed
        }

        failures.firstNotNullOfOrNull { it as? CertificateChangedException }?.let {
            return ServerCheck.CertificateChanged(it.host, it.fingerprint, it.pinnedFingerprint)
        }
        failures.firstOrNull { it is SSLHandshakeException || it is CertificateException }?.let {
            return ServerCheck.UntrustedCertificate(it.message ?: it::class.simpleName.orEmpty())
        }
        return ServerCheck.Unreachable(e.message ?: e::class.simpleName.orEmpty())
    }

    private fun parse(body: String): Answer = try {
        val envelope = json.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject
            ?: throw IllegalArgumentException("No subsonic-response")
        val error = envelope["error"] as? JsonObject

        Answer.Response(
            envelope = envelope,
            ok = envelope["status"]?.jsonPrimitive?.contentOrNull == "ok",
            info = ServerInfo(
                apiVersion = envelope.getValue("version").jsonPrimitive.content,
                type = envelope["type"]?.jsonPrimitive?.contentOrNull,
                serverVersion = envelope["serverVersion"]?.jsonPrimitive?.contentOrNull,
                isOpenSubsonic = envelope["openSubsonic"]?.jsonPrimitive?.boolean == true,
            ),
            errorCode = error?.get("code")?.jsonPrimitive?.int ?: 0,
            errorMessage = error?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    } catch (_: Exception) {
        Answer.Failure(ServerCheck.NotSubsonic)
    }

    private fun common() = listOf("v" to API_VERSION, "c" to CLIENT_NAME, "f" to "json")

    /**
     * Address of [method] with the sign in, for the player and the image loader that can only
     * be given an address. It identifies the user, so keep it out of logs.
     *
     * @return `null` when the server is off or not set up
     */
    private suspend fun endpoint(method: String, vararg params: Pair<String, String?>): Uri? {
        if (!settings.enabled || settings.url.isBlank()) return null
        val credentials = credentials() ?: return null

        return settings.url.trimEnd('/').toUri().buildUpon()
            .appendPath("rest")
            .appendPath(method)
            .apply {
                (credentials + common().filter { it.first != "f" }).forEach { (key, value) ->
                    appendQueryParameter(key, value)
                }
                params.forEach { (key, value) -> value?.let { appendQueryParameter(key, it) } }
            }
            .build()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /** The extension of servers that tell what they convert a song to. */
        const val TRANSCODING = "transcoding"

        /** Containers and codecs the player reads on every device, ALAC isn't one. */
        val DIRECT_PLAY_CONTAINERS =
            listOf("mp3", "mp4", "m4a", "aac", "flac", "ogg", "oga", "opus", "webm", "wav")
        val DIRECT_PLAY_CODECS = listOf("mp3", "aac", "flac", "vorbis", "opus", "pcm")

        /** Requests sent at once when one item needs many, like the albums of an artist. */
        const val PARALLEL_REQUESTS = 4

        /**
         * Songs searched for to fill an artist's top songs, a search also finds songs of other
         * artists that are left out.
         */
        const val TOP_SONGS_SEARCH = 50

        /** Largest page `getAlbumList2` accepts. */
        const val PAGE_SIZE = 500

        /** In kbps, `null` for the file as it is. */
        val StreamQuality.maxBitRate: Int?
            get() = when (this) {
                StreamQuality.LOW -> 64
                StreamQuality.MEDIUM -> 128
                // Lossy files are rarely above it, only lossless ones are converted
                StreamQuality.HIGH -> 320
                StreamQuality.ORIGINAL -> null
            }

        /**
         * What the player reads, for a transcode decision: the formats it plays as they are up
         * to [bitrate] bps, and what to convert the others to, Opus first.
         */
        fun clientInfo(bitrate: Int): JsonObject = buildJsonObject {
            put("name", CLIENT_NAME)
            put("platform", "Android")
            put("maxAudioBitrate", bitrate)
            put("maxTranscodingAudioBitrate", bitrate)
            putJsonArray("directPlayProfiles") {
                addJsonObject {
                    putJsonArray("containers") { DIRECT_PLAY_CONTAINERS.forEach { add(it) } }
                    putJsonArray("audioCodecs") { DIRECT_PLAY_CODECS.forEach { add(it) } }
                    putJsonArray("protocols") { add("http") }
                    put("maxAudioChannels", 8)
                }
            }
            putJsonArray("transcodingProfiles") {
                listOf("ogg" to "opus", "mp3" to "mp3").forEach { (container, codec) ->
                    addJsonObject {
                        put("container", container)
                        put("audioCodec", codec)
                        put("protocol", "http")
                        put("maxAudioChannels", 2)
                    }
                }
            }
            putJsonArray("codecProfiles") {}
        }
    }
}
