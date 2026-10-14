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
package me.knighthat.kreate3.ui.screen.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.youtube.YouTubeAccount
import me.knighthat.kreate3.provider.youtube.YouTubeChannel
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.isYouTubeSession
import me.knighthat.kreate3.provider.youtube.youTubeSyncSetting

/**
 * Where the YouTube account of the active profile stands.
 */
sealed interface YouTubeAccountState {

    data object SignedOut : YouTubeAccountState

    data object Checking : YouTubeAccountState

    data class SignedIn(val account: YouTubeAccount) : YouTubeAccountState

    /** The saved session no longer works, like after signing out of Google elsewhere. */
    data object Ended : YouTubeAccountState

    data class Failed(val reason: String) : YouTubeAccountState
}

/**
 * Signs the active profile in to YouTube and out of it, and picks which channel of the account it
 * acts as. The session is the cookies the sign in page left, kept in [Secrets.YOUTUBE_COOKIES]
 * and never anywhere else; the channel is [Preferences.YOUTUBE_PAGE_ID].
 *
 * @param scope the app's long lived scope, the library is taken out even if the screen closes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeAccountViewModel(
    private val provider: YouTubeProvider,
    private val library: YouTubeLibrary,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /** Whether a session is saved, and the channel acted as, each change asks YouTube again. */
    private val session = combine(
        Secrets.YOUTUBE_COOKIES.state,
        Preferences.YOUTUBE_PAGE_ID.state,
        attempt,
    ) { cookies, pageId, _ -> isYouTubeSession(cookies) to pageId }

    /** Who is signed in, as the channel acted as, asked from YouTube whenever it changes. */
    val account: StateFlow<YouTubeAccountState> =
        session
            .transformLatest { (signedIn) ->
                if (!signedIn) {
                    emit(YouTubeAccountState.SignedOut)
                    return@transformLatest
                }
                emit(YouTubeAccountState.Checking)
                emit(
                    try {
                        provider.account()?.let { YouTubeAccountState.SignedIn(it) }
                            ?: YouTubeAccountState.SignedOut
                    } catch (e: YouTubeException) {
                        if (e.signedOut) YouTubeAccountState.Ended
                        else YouTubeAccountState.Failed(e.message ?: "Can't reach YouTube")
                    }
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                YouTubeAccountState.Checking
            )

    /**
     * The channels of the signed in account, empty while nobody is signed in or they can't be
     * read. There is a choice to make only when there are several.
     */
    val channels: StateFlow<List<YouTubeChannel>> =
        // The channels are the same whichever is acted as, a switch keeps them
        combine(Secrets.YOUTUBE_COOKIES.state, attempt) { cookies, _ -> isYouTubeSession(cookies) }
            .transformLatest { signedIn ->
                emit(if (signedIn) channelsOrNone() else emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Page id of the channel acted as, empty for the account's own. */
    val pageId: StateFlow<String> = Preferences.YOUTUBE_PAGE_ID.state

    /** Asks YouTube again who is signed in. */
    fun retry() = attempt.update { it + 1 }

    /**
     * Keeps the session of [cookies], the ones of youtube.com once the sign in page is done, as
     * the account's own channel. Call [finishSignIn] once the channel is chosen, see
     * [channelsAfterSignIn].
     *
     * @return `false` when the cookies are not of a signed in session, nothing is kept then
     */
    fun signIn(cookies: String): Boolean {
        if (!isYouTubeSession(cookies)) return false
        Preferences.YOUTUBE_PAGE_ID.update("")
        Secrets.YOUTUBE_COOKIES.update(cookies)
        return true
    }

    /**
     * The channels of the account just signed in, the user chooses one when there are several.
     * Empty when they can't be read, the account's own channel is used then.
     */
    suspend fun channelsAfterSignIn(): List<YouTubeChannel> = channelsOrNone()

    /** Acts as [channel], `null` for the account's own, and brings the user's library in. */
    fun finishSignIn(channel: YouTubeChannel?) {
        Preferences.YOUTUBE_PAGE_ID.update(channel?.pageId.orEmpty())
        library.refreshAll()
    }

    /**
     * Acts as [channel] from now on. Its library replaces the one of the channel before.
     */
    fun switchTo(channel: YouTubeChannel) {
        if (channel.pageId.orEmpty() == Preferences.YOUTUBE_PAGE_ID.value) return
        Preferences.YOUTUBE_PAGE_ID.update(channel.pageId.orEmpty())
        library.refreshAll()
    }

    /**
     * Forgets the session and takes the YouTube library out of the profile's library. The
     * session itself stays valid at Google, only this app forgets it.
     */
    fun signOut() {
        Secrets.YOUTUBE_COOKIES.clear()
        Preferences.YOUTUBE_PAGE_ID.update("")
        scope.launch {
            try {
                library.clear()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't take the YouTube library out", e)
            }
        }
    }

    /**
     * Brings [section] of the YouTube library in from now on, or takes it out of the profile's
     * library when [on] is `false`.
     */
    fun setSyncing(section: LibrarySection, on: Boolean) {
        section.youTubeSyncSetting.update(on)
        if (on)
            library.refresh(section)
        else
            scope.launch {
                try {
                    library.clear(section)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Couldn't take $section of YouTube out", e)
                }
            }
    }

    private suspend fun channelsOrNone(): List<YouTubeChannel> =
        try {
            provider.channels().orEmpty()
        } catch (e: YouTubeException) {
            Log.w(TAG, "Couldn't read the channels of the account", e)
            emptyList()
        }

    private companion object {
        const val TAG = "YouTubeAccount"
    }
}
