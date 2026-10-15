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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
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
 * and never anywhere else; the channel is [Secrets.YOUTUBE_PAGE_ID]. The account is only used
 * while [Preferences.YOUTUBE_ACCOUNT_ENABLED] is on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeAccountViewModel(
    private val provider: YouTubeProvider,
    private val library: YouTubeLibrary,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /**
     * Whether a session is saved and in use, and the channel acted as, each change asks YouTube
     * again.
     */
    private val session = combine(
        Secrets.YOUTUBE_COOKIES.state,
        Preferences.YOUTUBE_ACCOUNT_ENABLED.state,
        Secrets.YOUTUBE_PAGE_ID.state,
        attempt,
    ) { cookies, enabled, pageId, _ -> (enabled && isYouTubeSession(cookies)) to pageId }

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
        combine(
            Secrets.YOUTUBE_COOKIES.state,
            Preferences.YOUTUBE_ACCOUNT_ENABLED.state,
            attempt,
        ) { cookies, enabled, _ -> enabled && isYouTubeSession(cookies) }
            .transformLatest { signedIn ->
                emit(if (signedIn) channelsOrNone() else emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Page id of the channel acted as, empty for the account's own. */
    val pageId: StateFlow<String> = Secrets.YOUTUBE_PAGE_ID.state

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
        Secrets.YOUTUBE_PAGE_ID.update("")
        Secrets.YOUTUBE_COOKIES.update(cookies)
        return true
    }

    /**
     * The channels of the account just signed in, the user chooses one when there are several.
     * Empty when they can't be read, the account's own channel is used then.
     */
    suspend fun channelsAfterSignIn(): List<YouTubeChannel> = channelsOrNone()

    /** Acts as [channel], `null` for the account's own, and brings the liked songs in. */
    fun finishSignIn(channel: YouTubeChannel?) {
        Secrets.YOUTUBE_PAGE_ID.update(channel?.pageId.orEmpty())
        library.refreshNow()
    }

    /**
     * Acts as [channel] from now on. Its liked songs replace the ones of the channel before.
     */
    fun switchTo(channel: YouTubeChannel) {
        if (channel.pageId.orEmpty() == Secrets.YOUTUBE_PAGE_ID.value) return
        Secrets.YOUTUBE_PAGE_ID.update(channel.pageId.orEmpty())
        library.refreshNow()
    }

    /**
     * Forgets the session. The likes it synced stay, and the session itself stays valid at Google,
     * only this app forgets it.
     */
    fun signOut() {
        Secrets.YOUTUBE_COOKIES.clear()
        Secrets.YOUTUBE_PAGE_ID.update("")
    }

    /**
     * Uses the account from now on, or sends every request signed out when [on] is `false`. The
     * session is kept either way, and what was synced stays.
     */
    fun setEnabled(on: Boolean) {
        Preferences.YOUTUBE_ACCOUNT_ENABLED.update(on)
        if (on) library.refreshNow()
    }

    /**
     * Shows [section] of the YouTube library from now on, or hides it when [on] is `false`. The
     * liked songs are synced instead, turning them off only stops the syncing.
     */
    fun setSyncing(section: LibrarySection, on: Boolean) {
        section.youTubeSyncSetting.update(on)
        if (section == LibrarySection.SONGS && on) library.refreshNow()
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
