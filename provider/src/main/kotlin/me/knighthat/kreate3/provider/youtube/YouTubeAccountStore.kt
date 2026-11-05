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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets

/**
 * Where the YouTube account of the active profile stands.
 */
sealed interface YouTubeAccountState {

    data object SignedOut : YouTubeAccountState

    data object Checking : YouTubeAccountState

    data class SignedIn(val account: YouTubeAccount) : YouTubeAccountState

    /** The saved session no longer works, like after signing out of Google elsewhere. */
    data object Ended : YouTubeAccountState

    /** @property reason what YouTube said, `null` when it didn't answer at all */
    data class Failed(val reason: String?) : YouTubeAccountState
}

/**
 * Who is signed in to YouTube, for everything that needs to know without asking YouTube again.
 * One lives as long as the app: it asks once the app starts and again whenever the session, the
 * channel acted as or [Preferences.YOUTUBE_ACCOUNT_ENABLED] change, or [refresh] is called.
 *
 * Nothing is asked while nobody is signed in or the account is off. [state] is
 * [YouTubeAccountState.Checking] until the saved session is read, so it never says signed out
 * only because it wasn't read yet.
 *
 * @param scope the app's long lived one
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeAccountStore(
    private val provider: YouTubeProvider,
    scope: CoroutineScope,
) {

    private val attempt = MutableStateFlow(0)
    private val _state = MutableStateFlow<YouTubeAccountState>(YouTubeAccountState.Checking)

    /** Who is signed in, as the channel acted as. */
    val state: StateFlow<YouTubeAccountState> = _state.asStateFlow()

    /** The signed in account, `null` while nobody is, or until it is known. */
    val account: YouTubeAccount?
        get() = (_state.value as? YouTubeAccountState.SignedIn)?.account

    val isSignedIn: Boolean
        get() = _state.value is YouTubeAccountState.SignedIn

    /**
     * Whether nobody is signed in: no session saved, the account is off or the session ended.
     * `false` while it isn't known, and when YouTube can't be reached.
     */
    val isLoggedOut: Boolean
        get() = _state.value.let {
            it == YouTubeAccountState.SignedOut || it == YouTubeAccountState.Ended
        }

    init {
        scope.launch {
            // Empty until the profile's files are read
            Secrets.YOUTUBE_COOKIES.current()
            Secrets.YOUTUBE_PAGE_ID.current()

            combine(
                Secrets.YOUTUBE_COOKIES.state,
                Preferences.YOUTUBE_ACCOUNT_ENABLED.state,
                Secrets.YOUTUBE_PAGE_ID.state,
                attempt,
            ) { cookies, enabled, _, _ -> enabled && isYouTubeSession(cookies) }
                .transformLatest { signedIn ->
                    if (!signedIn) {
                        emit(YouTubeAccountState.SignedOut)
                    } else {
                        emit(YouTubeAccountState.Checking)
                        emit(read())
                    }
                }
                .onEach { _state.value = it }
                .launchIn(this)
        }
    }

    /** Asks YouTube again who is signed in. */
    fun refresh() = attempt.update { it + 1 }

    private suspend fun read(): YouTubeAccountState =
        try {
            provider.account()?.let { YouTubeAccountState.SignedIn(it) }
                ?: YouTubeAccountState.SignedOut
        } catch (e: YouTubeException) {
            if (e.signedOut) YouTubeAccountState.Ended else YouTubeAccountState.Failed(e.message)
        }
}
