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

package me.knighthat.kreate3.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.parseQueryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

private const val TAG = "PatreonAuth"

/** How a Patreon sign in ended. */
sealed interface PatreonLogin {

    /** Signed in, the app stores both values of [tokens]. */
    class Success(val tokens: PatreonTokens) : PatreonLogin {
        override fun toString(): String = "Success"
    }

    /** The user declined, or Patreon reported an error: they may try again. */
    data object Declined : PatreonLogin

    /**
     * The sign in went stale (over 10 minutes, a lost cookie, or a code older than 2 minutes):
     * start it again.
     */
    data object Expired : PatreonLogin

    /** The API couldn't finish it: try again later. */
    data object Failed : PatreonLogin
}

/**
 * A sign in in the system browser, started by [PatreonAuth.start].
 *
 * @property url the API's login page, to open in the system browser or a Custom Tab
 * @property verifier proves the code is traded by the app that asked for it (PKCE). Kept only
 *   until [PatreonAuth.finish], never reused, never logged
 */
class PatreonSignIn internal constructor(val url: String, internal val verifier: String) {
    override fun toString(): String = "PatreonSignIn(hidden)"
}

/**
 * Signs a user in with Patreon through Kreate's API and swaps the refresh blob for new tokens.
 * Two ways in: the system browser with PKCE ([start], [finish]), preferred because Google blocks
 * its sign in inside web views, or an embedded web view ([webViewUrl], [webViewResult]).
 *
 * Keeps no state and no tokens: the app stores them in its secrets, and never logs them, the
 * codes or the verifiers. All requests fail with [ApiUnavailableException] when the build has no
 * [API_URL].
 *
 * @param http the app's client, so its proxy and timeouts apply
 * @param redirect the app's address the browser comes back to, e.g. `kreate3://patreon/done`. It
 *   must be on the API's allowlist, or the login page answers 400
 * @param apiUrl where the API is, [API_URL] outside tests
 */
class PatreonAuth(
    private val http: HttpClient,
    private val redirect: String,
    private val apiUrl: String = API_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** The login page a web view opens, see [webViewResult]. */
    val webViewUrl: String get() = "${requireApi()}/patreon/login"

    /**
     * A new sign in in the system browser. Open [PatreonSignIn.url], then hand the address the
     * browser comes back to to [finish]. Closing the browser brings nothing back: that's a cancel.
     *
     * @throws ApiUnavailableException when the build has no API address
     */
    fun start(): PatreonSignIn {
        val verifier = base64.encode(ByteArray(32).also(random::nextBytes))
        val challenge = base64.encode(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        val url = URLBuilder("${requireApi()}/patreon/login").apply {
            parameters.append("redirect", redirect)
            parameters.append("challenge", challenge)
        }.buildString()
        return PatreonSignIn(url, verifier)
    }

    /**
     * How the sign in [signIn] ended when [url] is the [redirect] the browser opened the app with,
     * trading its code for tokens; `null` for any other address.
     *
     * @throws IOException when the API can't be reached, or keeps failing
     */
    suspend fun finish(signIn: PatreonSignIn, url: String): PatreonLogin? {
        if (url.substringBefore('#').substringBefore('?') != redirect) return null

        val answer = parseQueryString(url.substringBefore('#').substringAfter('?', ""))
        val code = answer["code"]
        if (code.isNullOrEmpty()) return failure(answer)

        val response = http.post("${requireApi()}/patreon/token") {
            setBody(jsonBody("code" to code, "verifier" to signIn.verifier))
        }
        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val error = parse(body)?.text("error")
            when {
                response.status == HttpStatusCode.OK -> PatreonLogin.Success(tokensOf(body))
                // The code is over 2 minutes old, or the verifier isn't the one it was asked with
                error == "invalid_grant" -> PatreonLogin.Expired
                else -> {
                    Logger.w(tag = TAG) { "The API refused the code: ${response.status} $error" }
                    throw ApiException(response.status.value, error)
                }
            }
        }
    }

    /**
     * How the sign in ended when a web view that opened [webViewUrl] goes to [url]; `null` for
     * any other page. The web view stops loading on a non-`null` answer and closes: the result is
     * in the part after the `#`, which never reaches a server.
     */
    fun webViewResult(url: String): PatreonLogin? {
        if (apiUrl.isEmpty()) return null
        val done = runCatching { Url("$apiUrl/patreon/done") }.getOrNull() ?: return null
        val page = runCatching { Url(url) }.getOrNull() ?: return null
        if (page.protocol != done.protocol || page.host != done.host || page.port != done.port)
            return null
        if (page.encodedPath != done.encodedPath) return null

        val answer = parseQueryString(url.substringAfter('#', ""))
        val token = answer["token"]
        val refresh = answer["refresh"]
        return if (!token.isNullOrEmpty() && !refresh.isNullOrEmpty())
            PatreonLogin.Success(
                PatreonTokens(token, refresh, PatreonProfile.ofText(answer["profile"]))
            )
        else
            failure(answer)
    }

    /**
     * New tokens for the stored [refresh] blob. Save the new blob before using the new token, and
     * keep no old one as a fallback.
     *
     * The caller runs one refresh at a time for the whole app, reading the blob again after
     * waiting: the old blob may stop working once a refresh succeeds, so a second refresh with it
     * can sign the user out.
     *
     * @throws PatreonSignedOutException when the sign in is over (401): discard the blob and the
     *   token, the user signs in again. Only then, any other failure keeps the blob
     * @throws IOException when the API can't be reached, or fails (400, 502): retry later, not in
     *   a tight loop
     */
    suspend fun refresh(refresh: String): PatreonTokens {
        val response = http.post("${requireApi()}/patreon/refresh") {
            setBody(jsonBody("refresh" to refresh))
        }
        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            if (response.status == HttpStatusCode.OK) return@withContext tokensOf(body)

            val error = parse(body)?.text("error")
            Logger.w(tag = TAG) { "The API refused the refresh: ${response.status} $error" }
            if (response.status == HttpStatusCode.Unauthorized)
                throw PatreonSignedOutException("The API ended the sign in: $error")
            throw ApiException(response.status.value, error)
        }
    }

    private fun failure(answer: Parameters): PatreonLogin =
        when (answer["error"]) {
            "access_denied" -> PatreonLogin.Declined
            "invalid_state" -> PatreonLogin.Expired
            else -> PatreonLogin.Failed
        }

    private fun tokensOf(body: String): PatreonTokens {
        val answer = parse(body)
        val token = answer?.text("token")
        val refresh = answer?.text("refresh")
        if (token.isNullOrEmpty() || refresh.isNullOrEmpty())
            throw IOException("The API's answer has no tokens")
        return PatreonTokens(token, refresh, PatreonProfile.of(answer["profile"]))
    }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun jsonBody(vararg values: Pair<String, String>): TextContent =
        TextContent(
            buildJsonObject { values.forEach { (name, value) -> put(name, JsonPrimitive(value)) } }
                .toString(),
            ContentType.Application.Json,
        )

    private fun requireApi(): String {
        if (apiUrl.isEmpty()) throw ApiUnavailableException()
        return apiUrl
    }
}
