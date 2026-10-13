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

package me.knighthat.kreate.provider.opensubsonic

import android.net.Uri
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate.constant.OpenSubsonicAuth
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.UserPlaylist
import me.knighthat.kreate.database.repository.RemoteItem
import me.knighthat.kreate.provider.Provider
import me.knighthat.kreate.provider.SearchResults
import me.knighthat.kreate.provider.opensubsonic.OpenSubsonicProvider.Companion.PAGE_SIZE
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

private const val API_VERSION = "1.16.1"
private const val CLIENT_NAME = "kreate"

/**
 * A server that speaks the OpenSubsonic API, like Navidrome.
 *
 * It is set up by [OpenSubsonicSettings]. While they are off nothing is sent to the server and no
 * address is built, whatever address and credentials are saved. The library can be listed,
 * browsing into an item and searching are not written yet.
 */
class OpenSubsonicProvider internal constructor(
    private val client: () -> HttpClient,
    private val json: Json,
    private val settings: OpenSubsonicSettings,
) : Provider {

    /**
     * @param base the app's client, the provider derives its own from it, see [OpenSubsonicClients]
     */
    constructor(base: OkHttpClient, json: Json) : this(
        OpenSubsonicClients(base, ProfileOpenSubsonicSettings)::current,
        json,
        ProfileOpenSubsonicSettings,
    )

    override val origin = Origin.OPEN_SUBSONIC

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
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun libraryAlbums(): List<RemoteItem<Album>>? {
        serverUrl ?: return null

        val albums = mutableListOf<RemoteItem<Album>>()
        do {
            val page = call(
                "getAlbumList2",
                "type" to "alphabeticalByName",
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
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun libraryArtists(): List<RemoteItem<Artist>>? {
        serverUrl ?: return null

        return call("getArtists")["artists"]?.jsonObject?.array("index").orEmpty()
            .flatMap { it.jsonObject.array("artist").orEmpty() }
            .map { it.jsonObject.toArtist(::coverUri) }
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
     * Makes a public link to an album, playlist or song of the server. Anyone with it can open
     * and play the item without an account, until it is deleted or expires on the server.
     *
     * Every call makes a new link, keep it and reuse it while [shareUrls] still has it.
     *
     * @param itemId id of the item on the server
     * @param description shown on the shared page, like the item's name
     * @return the link, `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses, like when sharing is
     * turned off on the server
     */
    suspend fun createShare(itemId: String, description: String?): String? {
        serverUrl ?: return null

        val params = listOfNotNull("id" to itemId, description?.let { "description" to it })
        return call("createShare", *params.toTypedArray())["shares"]?.jsonObject
            ?.array("share")?.firstOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            ?: throw ServerException(ServerCheck.NotSubsonic)
    }

    /**
     * Links of the user's shares that still exist on the server.
     *
     * @return `null` while the server is off or not set up
     * @throws ServerException when the server can't be used or refuses the request
     */
    suspend fun shareUrls(): Set<String>? {
        serverUrl ?: return null

        return call("getShares")["shares"]?.jsonObject?.array("share").orEmpty()
            .mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
            .toSet()
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
        val base = serverUrl ?: return null
        if (!isCoverUri(uri) || uri.toString()
                .substringBefore('?') != "$base/rest/getCoverArt"
        ) return null
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
    private suspend fun call(method: String, vararg params: Pair<String, String>): JsonObject {
        val credentials = credentials() ?: throw ServerException(ServerCheck.NotConfigured)

        return when (val answer = request(method, credentials, params.toList())) {
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

    override suspend fun search(query: String, limit: Int, offset: Int): SearchResults =
        TODO(PENDING)

    override suspend fun song(songId: String): Song? = TODO(PENDING)

    override suspend fun album(albumId: String): Album? = TODO(PENDING)

    override suspend fun artist(artistId: String): Artist? = TODO(PENDING)

    override suspend fun songsOf(albumId: String): List<Song> = TODO(PENDING)

    override suspend fun albumsOf(artistId: String): List<Album> = TODO(PENDING)

    override suspend fun playbackUri(songId: String): Uri? = endpoint("stream", "id" to songId)

    /**
     * The cover address stored on items, which [cover] loads. The server scales it to the size
     * asked when it is loaded, so [size] is not part of it.
     */
    override fun artworkUri(artworkId: String, size: Int?): Uri? =
        coverUri(artworkId)

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
            val password = settings.password()
            if (settings.username.isEmpty() || password.isEmpty()) null
            else {
                val salt = ByteArray(8).also(SecureRandom()::nextBytes).toHex()
                val token = MessageDigest.getInstance("MD5")
                    .digest((password + salt).toByteArray()).toHex()
                listOf("u" to settings.username, "t" to token, "s" to salt)
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
    ): Answer {
        val body = try {
            client().submitForm(
                url = "${settings.url.trimEnd('/')}/rest/$method",
                formParameters = parameters {
                    (signIn + common() + params).forEach { (key, value) -> append(key, value) }
                },
            ).bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Answer.Failure(failureOf(e))
        }

        return parse(body)
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
        const val PENDING = "Requests to the server are not written yet"

        /** Largest page `getAlbumList2` accepts. */
        const val PAGE_SIZE = 500
    }
}
