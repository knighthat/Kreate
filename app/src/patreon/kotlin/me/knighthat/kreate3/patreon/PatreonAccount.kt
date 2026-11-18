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

package me.knighthat.kreate3.patreon

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.api.ApiUnavailableException
import me.knighthat.kreate3.api.PatreonAuth
import me.knighthat.kreate3.api.PatreonLogin
import me.knighthat.kreate3.api.PatreonProfile
import me.knighthat.kreate3.api.PatreonSignIn
import me.knighthat.kreate3.api.PatreonTokens
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.Secrets
import java.io.IOException

private const val TAG = "PatreonAccount"

/**
 * Where the API sends the browser once the user answered Patreon's page, on the API's allowlist.
 * `PatreonCallbackActivity` takes it (`app/src/patreon/AndroidManifest.xml`).
 */
internal const val PATREON_REDIRECT = "kreate3://patreon/done"

/** Who is signed in to Patreon, as the settings show it. */
sealed interface PatreonAccountState {

    data object SignedOut : PatreonAccountState

    /** The login page is open in the browser, the app waits for its answer. */
    data object SigningIn : PatreonAccountState

    /** @property profile who it is, `null` when the API didn't say */
    data class SignedIn(val profile: PatreonProfile?) : PatreonAccountState
}

/**
 * The device's Patreon sign in: signing in and out, and who is signed in (from [PatreonSession]).
 * The token, refresh blob and profile are device secrets ([Secrets.PATREON_TOKEN]…), the same for every profile.
 *
 * A sign in opens the API's login page in the browser ([start]); the API answers at
 * [PATREON_REDIRECT], which `PatreonCallbackActivity` hands to [finish]. What the sign in was
 * started with lives in memory only: an app closed meanwhile has to start again.
 */
class PatreonAccount internal constructor(
    private val context: Context,
    private val auth: PatreonAuth,
    session: PatreonSession,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {

    private val signingIn = MutableStateFlow<PatreonSignIn?>(null)

    // Whether the API answered the sign in started last, see abandon
    @Volatile
    private var answered = false

    /** Who is signed in, follows sign ins and sign outs. */
    val state: StateFlow<PatreonAccountState> =
        combine(session.member, signingIn) { member, pending ->
            when {
                pending != null -> PatreonAccountState.SigningIn
                member == null -> PatreonAccountState.SignedOut
                else -> PatreonAccountState.SignedIn(member.profile)
            }
        }.stateIn(scope, SharingStarted.Eagerly, PatreonAccountState.SignedOut)

    /**
     * Starts a sign in, the returned address is the login page to open; `null` when this build
     * has no API address, which is told to the user.
     */
    fun start(): String? {
        val signIn = try {
            auth.start()
        } catch (e: ApiUnavailableException) {
            Logger.w(e, TAG) { "No API to sign in to Patreon with" }
            tell(R.string.error_patreon_sign_in)
            return null
        }
        answered = false
        signingIn.value = signIn
        Logger.i(tag = TAG) { "Signing in to Patreon" }
        return signIn.url
    }

    /**
     * Forgets the sign in [start] began when the API never answered it, for when the app is back
     * on screen: the user left the login page another way.
     */
    fun abandon() {
        if (!answered) signingIn.value = null
    }

    /** Takes the API's answer at the redirect, [uri], and finishes the sign in it answers. */
    fun finish(uri: Uri) {
        answered = true
        val signIn = signingIn.value
        if (signIn == null) {
            // Not the answer to a sign in of this run, like one started before the app closed
            Logger.w(tag = TAG) { "An answer from the API to no sign in" }
            tell(R.string.error_patreon_sign_in_again)
            return
        }

        scope.launch {
            try {
                when (val login = auth.finish(signIn, uri.toString())) {
                    is PatreonLogin.Success -> {
                        save(login.tokens)
                        Logger.i(tag = TAG) { "Signed in to Patreon" }
                    }

                    PatreonLogin.Declined -> Logger.i(tag = TAG) { "Patreon's sign in was turned down" }
                    PatreonLogin.Expired -> tell(R.string.error_patreon_sign_in_again)
                    PatreonLogin.Failed, null -> tell(R.string.error_patreon_sign_in)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't finish signing in to Patreon" }
                tell(R.string.error_patreon_sign_in)
            } finally {
                signingIn.value = null
            }
        }
    }

    /** Signs the device out: the token, refresh blob and profile are forgotten. */
    fun signOut() {
        Secrets.PATREON_TOKEN.clear()
        Secrets.PATREON_REFRESH.clear()
        Secrets.PATREON_PROFILE.clear()
        Logger.i(tag = TAG) { "Signed out of Patreon" }
    }

    private fun save(tokens: PatreonTokens) {
        // The token last, it's what tells the app someone is signed in
        Secrets.PATREON_REFRESH.update(tokens.refresh)
        Secrets.PATREON_PROFILE.update(tokens.profile?.encode().orEmpty())
        Secrets.PATREON_TOKEN.update(tokens.token)
    }

    private fun tell(text: Int) {
        val resources = AppLanguage.wrap(context).resources
        notifier.post(resources.getString(text, resources.getString(R.string.patreon)))
    }
}
