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

package me.knighthat.kreate3.discord

import android.util.Base64
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "DiscordAuth"

/** Discord's page where the user lets the app in. */
private const val AUTHORIZE_URL = "https://discord.com/oauth2/authorize"

private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"

private const val REVOKE_URL = "https://discord.com/api/v10/oauth2/token/revoke"

private const val USER_URL = "https://discord.com/api/v10/users/@me"

/**
 * What the app asks for: who the user is, and setting their presence, the Social SDK's own
 * scope. Nothing else, a token that leaks can only set a status.
 */
const val DISCORD_SCOPES = "openid sdk.social_layer_presence"

/**
 * A sign in started by [DiscordAuth.start]: the page to open, and what the answer is checked and
 * traded with.
 *
 * @property url Discord's page asking the user to let the app in
 * @property state the answer must carry it back, or it isn't the answer to this sign in
 * @property verifier proves the code is traded by the app that asked for it (PKCE)
 */
class DiscordSignIn internal constructor(
    val url: String,
    val state: String,
    internal val verifier: String,
)

/** A Discord user, to show who is signed in. */
data class DiscordUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/** Discord no longer takes the tokens, the user has to sign in again. */
class DiscordSignedOutException(message: String) : IOException(message)

/**
 * Signs a user in with OAuth2 and PKCE, as a public client: the app keeps no secret, the verifier
 * of each sign in proves the code is its own.
 *
 * @param appId id of the app on Discord's developer portal
 * @param redirectUri where Discord sends the answer, as it's set on the portal
 */
class DiscordAuth(
    private val http: HttpClient,
    private val appId: String,
    private val redirectUri: String,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    /** A new sign in, the user lets the app in on [DiscordSignIn.url]. */
    fun start(): DiscordSignIn {
        val verifier = randomText(64)
        val state = randomText(24)
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        val url = URLBuilder(AUTHORIZE_URL).apply {
            parameters.append("client_id", appId)
            parameters.append("response_type", "code")
            parameters.append("redirect_uri", redirectUri)
            parameters.append("scope", DISCORD_SCOPES)
            parameters.append("state", state)
            parameters.append("code_challenge_method", "S256")
            parameters.append("code_challenge", challenge)
            // Asks every time, so another account can be picked
            parameters.append("prompt", "consent")
        }.buildString()
        return DiscordSignIn(url, state, verifier)
    }

    /**
     * Trades the [code] Discord answered [signIn] with for tokens.
     *
     * @throws IOException when Discord can't be reached or refuses
     */
    suspend fun finish(signIn: DiscordSignIn, code: String): DiscordTokens =
        tokens(
            "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to signIn.verifier,
        )

    /**
     * New tokens for the [refresh] token of the ones kept.
     *
     * @throws DiscordSignedOutException when Discord no longer takes it
     * @throws IOException when Discord can't be reached
     */
    suspend fun refresh(refresh: String): DiscordTokens =
        tokens("refresh_token", "refresh_token" to refresh)

    /** Tells Discord the [tokens] won't be used again. Failing is only logged. */
    suspend fun revoke(tokens: DiscordTokens) {
        try {
            val response = http.submitForm(
                REVOKE_URL,
                parameters {
                    append("client_id", appId)
                    append("token", tokens.refresh)
                    append("token_type_hint", "refresh_token")
                },
            )
            if (!response.status.isSuccess())
                Logger.w(tag = TAG) { "Discord didn't revoke the tokens: ${response.status}" }
        } catch (e: IOException) {
            Logger.w(e, TAG) { "Couldn't revoke the tokens" }
        }
    }

    /**
     * The user [access] signs in as.
     *
     * @throws DiscordSignedOutException when Discord no longer takes the token
     * @throws IOException when Discord can't be reached
     */
    suspend fun user(access: String): DiscordUser {
        val response = http.get(USER_URL) { header(HttpHeaders.Authorization, "Bearer $access") }
        if (response.status == HttpStatusCode.Unauthorized)
            throw DiscordSignedOutException("Discord refused the token")
        if (!response.status.isSuccess())
            throw IOException("Discord answered ${response.status}")

        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val user = json.parseToJsonElement(body).jsonObject
            val id = user.text("id") ?: throw IOException("Discord's answer has no user")
            DiscordUser(
                id = id,
                username = user.text("username").orEmpty(),
                displayName = user.text("global_name"),
                avatarUrl = user.text("avatar")
                    ?.let { "https://cdn.discordapp.com/avatars/$id/$it.png?size=128" },
            )
        }
    }

    private suspend fun tokens(grant: String, vararg values: Pair<String, String>): DiscordTokens {
        val response = http.submitForm(
            TOKEN_URL,
            parameters {
                append("client_id", appId)
                append("grant_type", grant)
                values.forEach { (name, value) -> append(name, value) }
            },
        )
        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val answer = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            if (!response.status.isSuccess()) {
                val error = answer?.text("error")
                Logger.w(tag = TAG) { "Discord refused the $grant grant: ${response.status} $error" }
                if (error == "invalid_grant")
                    throw DiscordSignedOutException("Discord refused the grant")
                throw IOException("Discord answered ${response.status}")
            }

            val access = answer?.text("access_token")
                ?: throw IOException("Discord's answer has no token")
            val expiresIn = answer["expires_in"]?.jsonPrimitive?.longOrNull ?: 0
            DiscordTokens(
                access = access,
                // A refresh may keep the one it was given
                refresh = answer.text("refresh_token") ?: values.toMap()["refresh_token"].orEmpty(),
                expiresAt = System.currentTimeMillis() / 1000 + expiresIn,
            )
        }
    }

    private fun randomText(bytes: Int): String =
        base64Url(ByteArray(bytes).also(random::nextBytes))

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

internal fun JsonObject.text(name: String): String? =
    (get(name) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
