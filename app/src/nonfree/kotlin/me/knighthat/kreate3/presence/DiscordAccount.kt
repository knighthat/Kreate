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

package me.knighthat.kreate3.presence

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.discord.DiscordAuth
import me.knighthat.kreate3.discord.DiscordSignIn
import me.knighthat.kreate3.discord.DiscordSignedOutException
import me.knighthat.kreate3.discord.DiscordTokens
import me.knighthat.kreate3.discord.DiscordUser
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.preferences.Secrets
import java.io.IOException

private const val TAG = "DiscordAccount"

/** Tokens are refreshed when they run out sooner than this, in seconds. */
private const val REFRESH_AHEAD = 24 * 60 * 60L

/** Who is signed in to Discord, as the settings page shows it. */
sealed interface DiscordAccountState {

    data object SignedOut : DiscordAccountState

    /** Discord's page is open, the app waits for its answer. */
    data object SigningIn : DiscordAccountState

    /** Signed in, the user isn't known yet or couldn't be asked. */
    data object SignedIn : DiscordAccountState

    data class Known(val user: DiscordUser) : DiscordAccountState
}

/**
 * Signing the active profile in to Discord and out, who is signed in, and the token to connect
 * with ([access]). The tokens are in [Secrets], so each profile has its own account; the
 * `:discord` module only ever gets the access token.
 *
 * A sign in opens Discord's page ([start]); Discord answers at the redirect, which
 * `DiscordCallbackActivity` hands to [finish]. What the sign in was started with lives in memory
 * only: an app closed meanwhile has to start again.
 */
class DiscordAccount internal constructor(
    private val context: Context,
    private val auth: DiscordAuth,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    private val profiles: ProfileManager,
) {

    private val signingIn = MutableStateFlow<DiscordSignIn?>(null)

    // Whether Discord answered the sign in started last, see abandon
    @Volatile
    private var answered = false
    private val user = MutableStateFlow<DiscordUser?>(null)
    private val _state = MutableStateFlow<DiscordAccountState>(DiscordAccountState.SignedOut)

    /** Who is signed in to the active profile, follows sign ins, sign outs and profile switches. */
    val state: StateFlow<DiscordAccountState> = _state.asStateFlow()

    init {
        scope.launch {
            // Another profile has another account, the one known isn't it
            profiles.activeId.collect { user.value = null }
        }
        scope.launch {
            combine(Secrets.DISCORD_ACCESS_TOKEN.state, signingIn, user) { token, pending, user ->
                when {
                    pending != null -> DiscordAccountState.SigningIn
                    token.isEmpty() -> DiscordAccountState.SignedOut
                    user != null -> DiscordAccountState.Known(user)
                    else -> DiscordAccountState.SignedIn
                }
            }.collect { _state.value = it }
        }
    }

    /** Starts a sign in, the returned address is Discord's page to open. */
    fun start(): String {
        val signIn = auth.start()
        answered = false
        signingIn.value = signIn
        Logger.i(tag = TAG) { "Signing in to Discord" }
        return signIn.url
    }

    /** Forgets the sign in [start] began, like when Discord's page couldn't be opened. */
    fun cancel() {
        signingIn.value = null
    }

    /**
     * Forgets the sign in [start] began when Discord never answered it, for when the app is back
     * on screen: the user left Discord's page another way.
     */
    fun abandon() {
        if (!answered) signingIn.value = null
    }

    /** Takes Discord's answer at the redirect, [uri], and finishes the sign in it answers. */
    fun finish(uri: Uri) {
        answered = true
        val signIn = signingIn.value
        val code = uri.getQueryParameter("code")
        when {
            signIn == null || uri.getQueryParameter("state") != signIn.state -> {
                // Not the answer to a sign in of this run, like one started before the app closed
                Logger.w(tag = TAG) { "An answer from Discord to no sign in" }
                signingIn.value = null
                tell(R.string.error_discord_sign_in_again)
            }

            code == null -> {
                Logger.i(tag = TAG) { "Discord's sign in was turned down: ${uri.getQueryParameter("error")}" }
                signingIn.value = null
            }

            else -> scope.launch {
                try {
                    val received = auth.finish(signIn, code)
                    save(received)
                    context.cancelDiscordSignedOut()
                    Logger.i(tag = TAG) { "Signed in to Discord" }
                    loadUser(received.access)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Logger.w(e, TAG) { "Couldn't finish signing in to Discord" }
                    tell(R.string.error_discord_sign_in)
                } finally {
                    signingIn.value = null
                }
            }
        }
    }

    /** Asks Discord who is signed in, once per profile; does nothing while nobody is. */
    fun refreshUser() {
        if (user.value != null) return
        scope.launch {
            val access = Secrets.DISCORD_ACCESS_TOKEN.current().ifEmpty { return@launch }
            loadUser(access)
        }
    }

    /** Signs the active profile out, telling Discord the tokens won't be used again. */
    fun signOut() {
        scope.launch { forget() }
    }

    /**
     * Like [signOut], returning once the tokens are forgotten; Discord is told afterwards on the
     * app's scope.
     */
    internal suspend fun forget() {
        val kept = load()
        clear()
        user.value = null
        Logger.i(tag = TAG) { "Signed out of Discord" }
        kept?.let { scope.launch { auth.revoke(it) } }
    }

    /**
     * The access token of [profile] to connect with, refreshed first when it runs out within a
     * day or [force] says so (the gateway refused it). `null` once nobody is signed in: there are
     * no tokens, or Discord no longer takes them and they were forgotten.
     *
     * @throws IOException when a refresh was needed and Discord couldn't be reached
     */
    internal suspend fun access(profile: String, force: Boolean): String? {
        val kept = load() ?: return null
        val now = System.currentTimeMillis() / 1000
        if (!force && kept.expiresAt - now > REFRESH_AHEAD) return kept.access

        Logger.i(tag = TAG) { "Refreshing Discord's tokens" }
        val refreshed = try {
            auth.refresh(kept.refresh)
        } catch (e: DiscordSignedOutException) {
            Logger.w(e, TAG) { "Discord no longer takes the tokens, signed out" }
            if (profiles.activeId.value == profile) {
                clear()
                context.postDiscordSignedOut()
            }
            return null
        } catch (e: IOException) {
            // Still good for a while, the next connection tries again
            if (!force && kept.expiresAt > now) return kept.access
            throw e
        }
        // Another profile is active now, its file isn't this account's
        if (profiles.activeId.value != profile) return null
        save(refreshed)
        return refreshed.access
    }

    /** The tokens of the active profile, `null` while nobody is signed in. */
    private suspend fun load(): DiscordTokens? {
        val access = Secrets.DISCORD_ACCESS_TOKEN.current().ifEmpty { return null }
        return DiscordTokens(
            access = access,
            refresh = Secrets.DISCORD_REFRESH_TOKEN.current(),
            expiresAt = Secrets.DISCORD_TOKEN_EXPIRES.current().toLongOrNull() ?: 0,
        )
    }

    private fun save(tokens: DiscordTokens) {
        // The access token last, it's what tells the app someone is signed in
        Secrets.DISCORD_REFRESH_TOKEN.update(tokens.refresh)
        Secrets.DISCORD_TOKEN_EXPIRES.update(tokens.expiresAt.toString())
        Secrets.DISCORD_ACCESS_TOKEN.update(tokens.access)
    }

    private fun clear() {
        Secrets.DISCORD_ACCESS_TOKEN.clear()
        Secrets.DISCORD_REFRESH_TOKEN.clear()
        Secrets.DISCORD_TOKEN_EXPIRES.clear()
    }

    private suspend fun loadUser(access: String) {
        try {
            user.value = auth.user(access)
        } catch (e: DiscordSignedOutException) {
            // The presence finds out too, and forgets them, see access
            Logger.w(e, TAG) { "Discord no longer takes the token" }
        } catch (e: IOException) {
            Logger.w(e, TAG) { "Couldn't ask Discord who is signed in" }
        }
    }

    private fun tell(text: Int) {
        val resources = AppLanguage.wrap(context).resources
        notifier.post(resources.getString(text, resources.getString(R.string.discord)))
    }
}
